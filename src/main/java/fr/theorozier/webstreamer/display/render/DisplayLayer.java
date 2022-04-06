package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.display.client.DisplayUrl;
import fr.theorozier.webstreamer.util.AsyncMap;
import fr.theorozier.webstreamer.util.AsyncProcessor;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.lindstrom.m3u8.parser.ParsingMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3i;
import org.bytedeco.javacv.Frame;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

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
	/** Interval of playlist requests when a past request has failed, to avoid spamming. */
	private static final long FAILING_PLAYLIST_INTERVAL = 5L * 1000000000L;

    private static class Inner {

		private final DisplayLayerResources res;
		
        private final DisplayUrl url;
        private final DisplayTexture tex;
    
        private final MediaPlaylistParser hlsParser;
	
	    /** In nanoseconds monotonic, last fetch time. */
	    private long lastFetch = 0;
	
	    // Playlist //

		/** The asynchronous processor */
		private final AsyncProcessor<URI, MediaPlaylist, IOException> asyncPlaylist;
		private long playlistNextRequestTimestamp;
	    /** Segments from the current playlist. */
	    private List<MediaSegment> playlistSegments;
	    /** Segment offset of the current playlist. */
	    private int playlistOffset;
		/** Last segment index observed when the last playlist request was done. */
		private int playlistRequestLastSegmentIndex;
		// /** Future of the current playlist request. */
	    // private Future<MediaPlaylist> playlistRequestFuture = null;
		
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

		private final AsyncMap<URI, FrameGrabber, IOException> asyncGrabbers;

//		/** A tuple of future and the time it was created at, used to clean up timed out grabbers. */
//		private record FutureGrabber(Future<FrameGrabber> future, long time) {
//
//			/**
//			 * Wait for the future and stop the grabber directly.
//			 */
//			void waitAndStop() {
//				try {
//					this.future.get().stop();
//				} catch (Exception ignored) { }
//			}
//
//			boolean isTimedOut(long now) {
//				return now - this.time >= GRABBER_REQUEST_TIMEOUT;
//			}
//
//		}
//
//		/**
//		 * Map of future grabbers for future mapped to future segments. Ultimately,
//		 * they might be unused and the cleanup is made for such cases.
//		 */
//	    private final Int2ObjectOpenHashMap<FutureGrabber> futureGrabbers = new Int2ObjectOpenHashMap<>();
		
		// Sound //
		/** The sound source. */
	    private final DisplaySoundSource soundSource;

		private Vec3i nearestSoundSourcePos;
		private float nearestSoundSourceDist;

		// Timing //
		/** Time in nanoseconds (monotonic) of the last use. */
		private long lastUse = 0;
		/** Time in nanoseconds (monotonic) of the last internal cleanup. */
		private long lastCleanup = 0;

        Inner(DisplayLayerResources res, DisplayUrl url) {

			this.res = res;
			
            this.url = url;
            this.tex = new DisplayTexture();

            this.hlsParser = new MediaPlaylistParser(ParsingMode.LENIENT);
			this.asyncPlaylist = new AsyncProcessor<>(this::requestPlaylistBlocking, true);
			this.asyncGrabbers = new AsyncMap<>(this::requestGrabberBlocking, FrameGrabber::stop, GRABBER_REQUEST_TIMEOUT);
			this.soundSource = new DisplaySoundSource();

			this.resetPlaylist();

			System.out.println("Allocate DisplayLayer for " + this.url);

        }

		private void free() {

			System.out.println("Free DisplayLayer for " + this.url);

			this.tex.clearGlId();
			this.asyncGrabbers.cleanup(this.res.getExecutor());
			this.soundSource.free();

		}

		private void resetSoundSource() {
			if (this.nearestSoundSourcePos != null) {
				this.soundSource.setPosition(this.nearestSoundSourcePos);
			} else {
				this.soundSource.stop();
			}
			this.nearestSoundSourcePos = null;
			this.nearestSoundSourceDist = Float.MAX_VALUE;
		}

		private void pushSoundSource(Vec3i pos, float dist) {
			if (dist < this.nearestSoundSourceDist) {
				this.nearestSoundSourcePos = pos;
				this.nearestSoundSourceDist = dist;
			}
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
        private MediaPlaylist requestPlaylistBlocking(URI uri) throws IOException {
			try {
				HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(5)).build();
				HttpResponse<Stream<String>> res = this.res.getHttpClient()
						.send(request, HttpResponse.BodyHandlers.ofLines());
				if (res.statusCode() == 200) {
					return this.hlsParser.readPlaylist(res.body().iterator());
				} else {
					throw new IOException("HTTP request failed, status code: " + res.statusCode());
				}
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
        }

		private void resetPlaylist() {
			this.playlistSegments = null;
			this.playlistNextRequestTimestamp = 0;
			this.playlistRequestLastSegmentIndex = -1;
		}
		
		/** Request the playlist if not already requesting and if this request is not pointless. */
		private void requestPlaylist() {
			int lastSegmentIndex = this.playlistSegments == null ? 0 : this.getLastSegmentIndex();
			if (lastSegmentIndex > this.playlistRequestLastSegmentIndex) {
				System.out.println("actually request playlist");
				this.playlistRequestLastSegmentIndex = lastSegmentIndex;
				this.asyncPlaylist.push(this.url.uri());
			}
		}
	
		private void fetchPlaylist() {
			this.asyncPlaylist.fetch(this.res.getExecutor(), playlist -> {
				this.playlistSegments = playlist.mediaSegments();
				this.playlistOffset = (int) playlist.mediaSequence();
			}, exc -> {
				// If failing, put timestamp to retry later.
				this.playlistNextRequestTimestamp = System.nanoTime() + FAILING_PLAYLIST_INTERVAL;
				this.playlistRequestLastSegmentIndex = -1;
			});
		}
		
		// Grabber //

		private FrameGrabber requestGrabberBlocking(URI uri) throws IOException {
			FrameGrabber grabber = new FrameGrabber(this.res, uri);
			grabber.start();
			return grabber;
		}
		
		private void requestGrabber(int index) {
			MediaSegment seg = this.getSegment(index);
			if (seg != null) {
				this.asyncGrabbers.push(this.res.getExecutor(), this.url.getContextUri(seg.uri()), index);
			}
		}

		/**
		 * Try to pull the given grabber, requested if not already.
		 * @param index The segment index to pull.
		 */
		private void pullGrabberAndUse(int index) {
			boolean requested = this.asyncGrabbers.pull(index, grabber -> {
				this.grabber = grabber;
			}, Throwable::printStackTrace);
			if (!requested) {
				this.requestGrabber(index);
			}
		}

		private void stopGrabber(boolean grabRemaining) {
			if (this.grabber != null) {
				if (grabRemaining) {
					try {
						this.grabber.grabRemaining();
						this.grabber.grabAudioAndUpload(this.soundSource);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				this.res.getExecutor().execute(this.grabber::stop);
				this.grabber = null;
			}
		}
	
        private void fetch() throws IOException {
			
            long currentTime = System.nanoTime();
            double elapsedTime = ((double) (currentTime - this.lastFetch) / 1000000000.0);
            this.lastFetch = currentTime;

			if (this.playlistSegments != null) {
	
				// Tries to pull the playlist if being requested.
	            this.fetchPlaylist();
				
				// This algorithm tries to go forward in segments by elapsedTime.
				double remainingTime = elapsedTime;
	
				for (;;) {
					
					// If we are too slow and the current segment is now out of the playlist.
					if (this.getCurrentSegment() == null) {
						this.resetPlaylist();
						this.stopGrabber(true);
						break;
					}
					
					this.segmentTimestamp += remainingTime;
					if (this.segmentTimestamp > this.segmentDuration) {

						this.segmentIndex++;
						MediaSegment seg = this.getCurrentSegment();
						
						if (seg == null) {
							this.resetPlaylist();
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
	
	        if (this.playlistSegments == null) {

				if (this.playlistNextRequestTimestamp < 0) {
					// After request, we go here.
					this.fetchPlaylist();
					if (this.playlistSegments == null)
						return;
				} else if (currentTime >= this.playlistNextRequestTimestamp) {
					// In first place with fall here after resetPlaylist()
					this.playlistNextRequestTimestamp = -1;
					this.requestPlaylist();
					return;
				} else {
					// If there is no current request or if the current time is too early, just return.
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
				this.pullGrabberAndUse(this.segmentIndex);
				if (this.grabber == null) {
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
				this.asyncGrabbers.cleanupTimedOut(this.res.getExecutor(), now);
				this.lastCleanup = now;
			}
	
        }

    }
	
	private final Inner inner;

    public DisplayLayer(DisplayLayerResources res, DisplayUrl url) {
		// We are using an inner class just for the "super" call to be first.
        this(new Inner(res, url));
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
		        RenderSystem::disableDepthTest);
		
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

	public void resetSoundSource() {
		this.inner.resetSoundSource();
	}

	public void pushSoundSource(Vec3i pos, float dist) {
		this.inner.pushSoundSource(pos, dist);
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
