package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.display.sound.DisplaySoundSource;
import fr.theorozier.webstreamer.source.Source;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.lindstrom.m3u8.parser.ParsingMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3i;
import org.bytedeco.javacv.Frame;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * There is only instance of this class per source.
 */
public class DisplayLayer extends RenderLayer {

    private static class Inner {

		private final DisplayLayerManager manager;
		
        private final Source source;
        private final DisplayTexture tex;
    
        private final MediaPlaylistParser hlsParser;
	
	    /** In nanoseconds monotonic, last fetch time. */
	    private long lastFetch = 0;
	
	    // Playlist //
	
	    /** Segments from the current playlist. */
	    private List<MediaSegment> playlistSegments = null;
	    /** Segment offset of the current playlist. */
	    private int playlistOffset = 0;
		/** Future of the current playlist request. */
	    private Future<MediaPlaylist> playlistRequestFuture = null;
		/** Last segment index observed when the last playlist request was done. */
		private int playlistRequestLastSegmentIndex = -1;
		
		// Segment //
	    
	    /** Absolute index of the current segment. */
        private int segmentIndex = -1;
		/** Timestamp within the current segment. */
        private double segmentTimestamp = 0.0;
		/** Duration of the current segment. */
        private double segmentDuration = 0.0;
		
		// Grabber //
	 
		/** Frame grabber for the current segment. */
        private FrameGrabber grabber;
	    private record FutureGrabber(Future<FrameGrabber> future, long requestTime) {}
	    private final Int2ObjectOpenHashMap<FutureGrabber> futureGrabbers = new Int2ObjectOpenHashMap<>();
		
		// Sound //
	    // Sounds are handled in the render layer because they must be perfectly synced to the visual
	    private final DisplaySoundSource soundSource;
		
        Inner(DisplayLayerManager manager, Source source) {

			this.manager = manager;
			
            this.source = source;
            this.tex = new DisplayTexture();
            
            this.hlsParser = new MediaPlaylistParser(ParsingMode.LENIENT);
			
			this.soundSource = new DisplaySoundSource();
    
        }
		
		// Playlist //
	
	    /**
	     * Return the segment at the given absolute index.
	     * @param index The absolute index.
	     * @return The media segment, or null if out of bounds.
	     */
	    private MediaSegment getSegment(int index) {
		    try {
			    return this.playlistSegments.get(index - this.playlistOffset);
		    } catch (IndexOutOfBoundsException e) {
			    return null;
		    }
	    }
	
	    /** @return The current media segment. */
	    private MediaSegment getCurrentSegment() {
		    return this.getSegment(this.segmentIndex);
	    }
	
	    /** @return The first segment index for the current playlist. */
		private int getFirstSegmentIndex() {
			return this.playlistOffset;
		}
	
	    /** @return The last segment index for the current playlist. */
	    private int getLastSegmentIndex() {
		    return this.playlistSegments.size() - 1 + this.playlistOffset;
	    }
     
		/** Internal blocking method to request the playlist. */
        private MediaPlaylist requestPlaylistBlocking() throws IOException {
            return this.hlsParser.readPlaylist(this.source.getUrl().openStream());
        }
		
		/** Request the playlist if not already requesting and if this request is not pointless. */
		private void requestPlaylist() {
			int lastSegmentIndex = this.playlistSegments == null ? 0 : this.getLastSegmentIndex();
			if (this.playlistRequestFuture == null && lastSegmentIndex > this.playlistRequestLastSegmentIndex) {
				System.out.println("=> Requesting playlist...");
				this.playlistRequestFuture = this.manager.getExecutor().submit(this::requestPlaylistBlocking);
				this.playlistRequestLastSegmentIndex = lastSegmentIndex;
			}
		}
	
	    /** @return True if the playlist is set and ready to be used. */
		private boolean pullPlaylist() {
			if (this.playlistRequestFuture != null) {
				if (this.playlistRequestFuture.isDone()) {
					if (!this.playlistRequestFuture.isCancelled()) {
						try {
							MediaPlaylist playlist = this.playlistRequestFuture.get();
							this.playlistSegments = playlist.mediaSegments();
							this.playlistOffset = (int) playlist.mediaSequence();
							this.playlistRequestFuture = null;
							return true;
						} catch (InterruptedException e) {
							return false; // Do nothing else to allow retry.
						} catch (ExecutionException ignored) {
							// Go to return FAILED
						}
					}
					this.playlistRequestFuture = null;
					this.playlistRequestLastSegmentIndex = -1;
				}
			}
			return false;
		}
		
		// Grabber //
	    
	    // TODO: Add a cleanup function for never-used grabbers.
		
		private void requestGrabber(int index) {
			MediaSegment seg = this.getSegment(index);
			if (seg != null) {
				this.futureGrabbers.computeIfAbsent(index, index0 -> {
					System.out.println("=> Request grabber for segment " + index0 + "/" + this.getLastSegmentIndex());
					return new FutureGrabber(this.manager.getExecutor().submit(() -> {
						URL segmentUrl = this.source.getContextUrl(seg.uri());
						FrameGrabber grabber = new FrameGrabber(segmentUrl.openStream());
						grabber.start();
						return grabber;
					}), System.nanoTime());
				});
			}
		}
		
		private boolean pullGrabberAndUse(int index) {
			FutureGrabber futureGrabber = this.futureGrabbers.get(index);
			if (futureGrabber != null) {
				Future<FrameGrabber> future = futureGrabber.future;
				if (future.isDone()) {
					if (!future.isCancelled()) {
						try {
							FrameGrabber grabber = future.get();
							this.stopAndRemoveGrabber();
							this.grabber = grabber;
							// This grabber should have been started by the task.
							this.futureGrabbers.remove(index);
							return true;
						} catch (InterruptedException e) {
							return false; // Do nothing else to allow retry.
						} catch (ExecutionException ignored) {
							// Go next to remove.
						}
					}
					this.futureGrabbers.remove(index);
				}
			} else {
				this.requestGrabber(index);
			}
			return false;
		}
	
		private void stopAndRemoveGrabber() {
			if (this.grabber != null) {
				FrameGrabber grabber = this.grabber;
				this.manager.getExecutor().execute(() -> {
					try {
						grabber.stop();
					} catch (IOException ignored) {}
				});
				this.grabber = null;
			}
		}
	
	    /**
	     * Fetch the next segment and its timestamp.
	     * @return True if a valid segment is available to process.
	     * @throws IOException If any error happens when fetching online playlist.
	     */
        private boolean fetchSegment() throws IOException {
			
			// This is the latency we force from the last segment.
            final double SAFE_LATENCY = 6.0;
            
			// The speed factor can be adjusted by various elements.
			double speedFactor = 1.0;
			
			if (this.segmentIndex == this.playlistOffset) {
				// If we are in the first segment, add a little speed factor in order to avoid
				// getting out of the playlist.
				speedFactor = 1.1;
			}
			
            long currentTime = System.nanoTime();
            double elapsedTime = ((double) (currentTime - this.lastFetch) / 1000000000.0) * speedFactor;
            this.lastFetch = currentTime;
	
            if (this.segmentIndex != -1) {
	
	            long segmentStart = System.nanoTime();
	
				// Tries to pull the playlist if being requested.
	            this.pullPlaylist();
				
				// This algorithm tries to go forward in segments by elapsedTime.
				double remainingTime = elapsedTime;
	
	            long loopStart = System.nanoTime();
				
				for (;;) {
					
					// If we are too slow and the current segment is now out of the playlist.
					if (this.getCurrentSegment() == null) {
						this.segmentIndex = -1;
						this.playlistRequestLastSegmentIndex = -1;  // Temporary fix to avoid init infinite loop
						this.stopAndRemoveGrabber();
						break;
					}
					
					this.segmentTimestamp += remainingTime;
					if (this.segmentTimestamp > this.segmentDuration) {
						
						System.out.println("Segment " + this.segmentIndex + "/" + this.getLastSegmentIndex() + " overflow...");
						System.out.println("=> Time " + this.segmentTimestamp + " > " + this.segmentDuration);
						
						this.segmentIndex++;
						MediaSegment seg = this.getCurrentSegment();
						
						if (seg == null) {
							System.out.println("=> Next segment is null, re-sync...");
							this.segmentIndex = -1;
							this.playlistRequestLastSegmentIndex = -1;  // Temporary fix to avoid init infinite loop
							this.stopAndRemoveGrabber();
							break;
						}
						
						System.out.println("=> Going next segment and removing grabber...");
						this.stopAndRemoveGrabber();
						remainingTime = this.segmentTimestamp - this.segmentDuration;
						this.segmentDuration = seg.duration();
						this.segmentTimestamp = 0;
						
					} else {
						break;
					}
					
				}
				
	            printTime("segment loop", loopStart);
	
				int offsetFromLastSegment = this.getLastSegmentIndex() - this.segmentIndex;
				
				if (offsetFromLastSegment <= 1) {
					long requestStart = System.nanoTime();
					// We are at most 1 segment from the end, so request a new playlist.
					this.requestPlaylist();
					printTime("request playlist", requestStart);
				}
				
				if (offsetFromLastSegment >= 1) {
					long requestStart = System.nanoTime();
					// If we have at least one segment after the current one, preload it.
					this.requestGrabber(this.segmentIndex + 1);
					printTime("request grabber", requestStart);
				}
	
	            printTime("segment", segmentStart);
	
            }
	
	        if (this.segmentIndex == -1) {
		
		        System.out.println("Display (re)initialization...");
				
		        // Here we need use this complex algorithm to seek
		        // 'SAFE_LATENCY' seconds before the end.
		
		        if (!this.pullPlaylist()) {
					this.requestPlaylist();
			        return false;
		        }
		        
		        double totalDuration = 0.0;
		        for (MediaSegment seg : this.playlistSegments) {
			        totalDuration += seg.duration();
		        }
		
		        this.stopAndRemoveGrabber();
		        double globalTimestamp = totalDuration;
		
		        this.segmentIndex = this.playlistOffset + (this.playlistSegments.size() - 1);
		        MediaSegment seg = this.getCurrentSegment();
				
		        this.segmentDuration = seg.duration();
		        this.segmentTimestamp = this.segmentDuration;
		
		        for (;;) {
			
			        double latency = totalDuration - globalTimestamp;
			        double latencyDiff = SAFE_LATENCY - latency;
			
			        if (seg.duration() >= latencyDiff) {
				        this.segmentTimestamp -= latencyDiff;
				        break;
			        } else {
				        this.segmentIndex -= 1;
						seg = this.getCurrentSegment();
						if (seg == null) {
							this.segmentIndex = 0;
							this.segmentTimestamp = 0;
							break;
						} else {
							globalTimestamp -= seg.duration();
							this.segmentDuration = seg.duration();
							this.segmentTimestamp = this.segmentDuration;
						}
			        }
			
		        }
		
	        }
			
            if (this.grabber == null) {
				return this.pullGrabberAndUse(this.segmentIndex);
            }
			
			return true;
            
        }
	 
	    private void fetchFrame() throws IOException {
		    Frame frame;
		    while ((frame = this.grabber.grab()) != null) {
			    if (frame.image != null) {
				    double frameTimestamp = (double) frame.timestamp / 1000000.0;
				    if (frameTimestamp >= this.segmentTimestamp) {
					    this.tex.upload(frame);
						break;
				    }
			    }
				if (frame.samples != null) {
					this.soundSource.uploadAndEnqueue(frame);
					this.soundSource.unqueueAndDelete();
				}
		    }
	    }

        private void tick() {
	
	        System.out.println("--------------------");
	        long tickStart = System.nanoTime();
			
	        try {
		        if (this.fetchSegment()) {
					long frameStart = System.nanoTime();
			        this.fetchFrame();
			        printTime("frame", frameStart);
		        }
	        } catch (IOException e) {
		        e.printStackTrace();
	        }
	
	        printTime("tick", tickStart);

        }
		
		private static void printTime(String name, long start) {
			System.out.println(name + ": " + ((double) (System.nanoTime() - start) / 1000000.0) + "ms");
		}

    }
	
	private final Inner inner;

    public DisplayLayer(DisplayLayerManager manager, Source source) {
		// We are using an inner class just for the "super" call to be first.
        this(new Inner(manager, source));
    }

    private DisplayLayer(Inner inner) {
		
        super("display", VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS,
                256, false, true,
                () -> {
                    POSITION_TEXTURE_SHADER.startDrawing();
                    RenderSystem.enableTexture();
                    RenderSystem.setShaderTexture(0, inner.tex.getGlId());
                },
                () -> {});
		
		this.inner = inner;
		
    }
	
	public void tickDisplay() {
		this.inner.tick();
	}
	
	public void setDisplayPosition(Vec3i pos) {
		this.inner.soundSource.setPosition(pos);
	}

}
