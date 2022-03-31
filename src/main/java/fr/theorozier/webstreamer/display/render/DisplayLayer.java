package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.display.DisplaySourceUrl;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.lindstrom.m3u8.parser.ParsingMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3i;
import org.bytedeco.javacv.Frame;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * There is only instance of this class per source.
 * Every call to this class must be done from render thread,
 * this is not checked so user have to check this!
 */
@Environment(EnvType.CLIENT)
public class DisplayLayer extends RenderLayer {

	/** The latency forced, avoiding display freezes for loading. */
	private static final double SAFE_LATENCY = 8.0;
	/** The timeout for a layer to be considered unused */
	private static final long LAYER_UNUSED_TIMEOUT = 5L * 1000000000L;
	/** The timeout for grabber's request. */
	private static final long GRABBER_REQUEST_TIMEOUT = 10L * 1000000000L;
	/** Interval of internal cleanups (unused grabbers). */
	private static final long CLEANUP_INTERVAL = 10L * 1000000000L;

    private static class Inner {

		private final ExecutorService executor;
		
        private final DisplaySourceUrl url;
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

		/** True if this is the first grabber after an initialisation. */
		private boolean firstGrabber;

		/** A tuple of future and the time it was created at, used to cleanup timed out grabbers. */
		private record FutureGrabber(Future<FrameGrabber> future, long time) {

			/**
			 * Wait for the future and stop the grabber directly.
			 */
			void waitAndStop() {
				try {
					this.future.get().stop();
				} catch (Exception ignored) { }
			}

			boolean isTimedOut(long now) {
				return now - this.time >= GRABBER_REQUEST_TIMEOUT;
			}

		}

		/**
		 * Map of future grabbers for future mapped to future segments. Ultimately,
		 * they might be unused and the cleanup is made for such cases.
		 */
	    private final Int2ObjectOpenHashMap<FutureGrabber> futureGrabbers = new Int2ObjectOpenHashMap<>();
		
		// Sound //
		/** The sound source. */
	    private final DisplaySoundSource soundSource;

		// Timing //
		/** Time in nanoseconds (monotonic) of the last use. */
		private long lastUse = 0;
		/** Time in nanoseconds (monotonic) of the last internal cleanup. */
		private long lastCleanup = 0;

        Inner(ExecutorService executor, DisplaySourceUrl url) {

			this.executor = executor;
			
            this.url = url;
            this.tex = new DisplayTexture();
            
            this.hlsParser = new MediaPlaylistParser(ParsingMode.LENIENT);
			
			this.soundSource = new DisplaySoundSource();

			System.out.println("Allocate DisplayLayer for " + this.url);
    
        }

		private void free() {

			System.out.println("Free DisplayLayer for " + this.url);

			this.tex.clearGlId();
			this.soundSource.free();
			for (FutureGrabber grabber : this.futureGrabbers.values()) {
				this.executor.execute(grabber::waitAndStop);
			}
			this.futureGrabbers.clear();

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
	
	    /** @return The last segment index for the current playlist. */
	    private int getLastSegmentIndex() {
		    return this.playlistSegments.size() - 1 + this.playlistOffset;
	    }
     
		/** Internal blocking method to request the playlist. */
        private MediaPlaylist requestPlaylistBlocking() throws IOException {
            return this.hlsParser.readPlaylist(this.url.url().openStream());
        }
		
		/** Request the playlist if not already requesting and if this request is not pointless. */
		private void requestPlaylist() {
			int lastSegmentIndex = this.playlistSegments == null ? 0 : this.getLastSegmentIndex();
			if (this.playlistRequestFuture == null && lastSegmentIndex > this.playlistRequestLastSegmentIndex) {
				// System.out.println("=> Requesting playlist...");
				this.playlistRequestFuture = this.executor.submit(this::requestPlaylistBlocking);
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
	    
		private void cleanupUnusedGrabbers(long now) {
			Iterator<FutureGrabber> it = this.futureGrabbers.values().iterator();
			while (it.hasNext()) {
				FutureGrabber item = it.next();
				if (item.isTimedOut(now)) {
					this.executor.execute(item::waitAndStop);
					it.remove();
				}
			}
		}
		
		private void requestGrabber(int index) {
			MediaSegment seg = this.getSegment(index);
			if (seg != null) {
				this.futureGrabbers.computeIfAbsent(index, index0 -> {
					// System.out.println("=> Request grabber for segment " + index0 + "/" + this.getLastSegmentIndex());
					return new FutureGrabber(this.executor.submit(() -> {
						URL segmentUrl = this.url.getContextUrl(seg.uri());
						FrameGrabber grabber = new FrameGrabber(segmentUrl.openStream());
						grabber.start();
						return grabber;
					}), System.nanoTime());
				});
			}
		}
		
		private boolean pullGrabberAndUse(int index) throws IOException {
			FutureGrabber futureGrabber = this.futureGrabbers.get(index);
			if (futureGrabber != null) {
				Future<FrameGrabber> future = futureGrabber.future;
				if (future.isDone()) {
					if (!future.isCancelled()) {
						try {
							FrameGrabber grabber = future.get();
							this.stopGrabber(true);
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

		private void stopGrabber(boolean grabRemaining) throws IOException {
			if (this.grabber != null) {
				if (grabRemaining) {
					this.grabber.grabRemaining();
					this.grabber.grabAudioAndUpload(this.soundSource);
				}
				this.executor.execute(this.grabber::stop);
				this.grabber = null;
			}
		}
	
        private void fetch() throws IOException {
            
			// The speed factor can be adjusted by various elements.
			double speedFactor = 1.0;
			
			if (this.segmentIndex == this.playlistOffset) {
				System.out.println("Fast forward enabled...");
				// If we are in the first segment, add a little speed factor in order to avoid
				// getting out of the playlist.
				speedFactor = 1.1;
			}
			
            long currentTime = System.nanoTime();
            double elapsedTime = ((double) (currentTime - this.lastFetch) / 1000000000.0) * speedFactor;
            this.lastFetch = currentTime;
	
            if (this.segmentIndex != -1) {
	
				// Tries to pull the playlist if being requested.
	            this.pullPlaylist();
				
				// This algorithm tries to go forward in segments by elapsedTime.
				double remainingTime = elapsedTime;
	
				for (;;) {
					
					// If we are too slow and the current segment is now out of the playlist.
					if (this.getCurrentSegment() == null) {
						this.segmentIndex = -1;
						this.playlistRequestLastSegmentIndex = -1;  // Temporary fix to avoid init infinite loop
						this.stopGrabber(true);
						break;
					}
					
					this.segmentTimestamp += remainingTime;
					if (this.segmentTimestamp > this.segmentDuration) {

						//System.out.println("Segment " + this.segmentIndex + "/" + this.getLastSegmentIndex() + " overflow...");
						//System.out.println("=> Time " + this.segmentTimestamp + " > " + this.segmentDuration);

						this.segmentIndex++;
						MediaSegment seg = this.getCurrentSegment();
						
						if (seg == null) {
							//System.out.println("=> Next segment is null, re-sync...");
							this.segmentIndex = -1;
							this.playlistRequestLastSegmentIndex = -1;  // Temporary fix to avoid init infinite loop
							this.stopGrabber(true);
							break;
						}
						
						//System.out.println("=> Going next segment and removing grabber...");
						this.stopGrabber(true);
						remainingTime = this.segmentTimestamp - this.segmentDuration;
						this.segmentDuration = seg.duration();
						this.segmentTimestamp = 0;
						
					} else {
						break;
					}
					
				}
				
				int offsetFromLastSegment = this.getLastSegmentIndex() - this.segmentIndex;
				
				if (offsetFromLastSegment <= 1) {
					// We are at most 1 segment from the end, so request a new playlist.
					this.requestPlaylist();
				}
				
				if (offsetFromLastSegment >= 1) {
					// If we have at least one segment after the current one, preload it.
					this.requestGrabber(this.segmentIndex + 1);
				}
	
            }
	
	        if (this.segmentIndex == -1) {
		
		        if (!this.pullPlaylist()) {
					this.requestPlaylist();
			        return;
		        }
		        
		        double totalDuration = 0.0;
		        for (MediaSegment seg : this.playlistSegments) {
			        totalDuration += seg.duration();
		        }
		
		        this.stopGrabber(false);
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

				this.firstGrabber = true;
		
	        }
			
            if (this.grabber == null) {
				if (!this.pullGrabberAndUse(this.segmentIndex)) {
					return;
				}
            }

			long segmentTimestampMicros = (long) (this.segmentTimestamp * 1000000);

			Frame frame = this.grabber.grabAt(segmentTimestampMicros);
			if (frame != null) {
				this.tex.upload(frame);
			}

			if (this.firstGrabber) {
				this.firstGrabber = false;
				System.out.println("skip audio before " + this.segmentTimestamp);
				this.grabber.skipAudioBufferBefore(segmentTimestampMicros);
			}

			this.grabber.grabAudioAndUpload(this.soundSource);
			
        }

        private void tick() {
	
	        try {
				this.fetch();
	        } catch (IOException e) {
		        e.printStackTrace();
	        }

			long now = System.nanoTime();
			if (now - this.lastCleanup >= CLEANUP_INTERVAL) {
				this.cleanupUnusedGrabbers(now);
				this.lastCleanup = now;
			}
	
        }

    }
	
	private final Inner inner;

    public DisplayLayer(ExecutorService executor, DisplaySourceUrl url) {
		// We are using an inner class just for the "super" call to be first.
        this(new Inner(executor, url));
    }

    private DisplayLayer(Inner inner) {
		
        super("display", VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS,
                256, false, true,
                () -> {
					inner.lastUse = System.nanoTime();
                    POSITION_TEXTURE_SHADER.startDrawing();
					RenderSystem.enableDepthTest();
					RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    RenderSystem.enableTexture();
                    RenderSystem.setShaderTexture(0, inner.tex.getGlId());
                },
                () -> {
					RenderSystem.disableDepthTest();
				});
		
		this.inner = inner;
		
    }

	/**
	 * Free this display layer. <b>It should not be used again after!</b>
	 */
	public void displayFree() {
		this.inner.free();
	}

	/**
	 * Tick the display layer.
	 */
	public void displayTick() {
		this.inner.tick();
	}

	/**
	 * Set the position of the display, used to change the sound source.
	 * @param pos The position of the display.
	 */
	public void displaySetPos(Vec3i pos) {
		// TODO: Use only the nearest source as the main and single source.
		this.inner.soundSource.setPosition(pos);
	}

	/**
	 * Check if this layer is unused for too long, in such case
	 * @param now The reference timestamp (monotonic nanoseconds
	 *               from {@link System#nanoTime()}).
	 * @return True if the layer is unused for too long.
	 */
	public boolean displayIsUnused(long now) {
		return now - this.inner.lastUse >= LAYER_UNUSED_TIMEOUT;
	}

}
