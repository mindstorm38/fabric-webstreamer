package fr.theorozier.webstreamer.display.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.audio.AudioStreamingSource;
import fr.theorozier.webstreamer.display.url.DisplayUrl;
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
import net.minecraft.util.profiler.ProfilerSystem;
import net.minecraft.util.profiler.ReadableProfiler;
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
	private static final long LAYER_UNUSED_TIMEOUT = 15L * 1000000000L;
	/** The timeout for grabber's request. */
	private static final long GRABBER_REQUEST_TIMEOUT = 10L * 1000000000L;
	/** Interval of internal cleanups (unused grabbers). */
	private static final long CLEANUP_INTERVAL = 10L * 1000000000L;
	
	private static final long INITIAL_PLAYLIST_REQUEST_INTERVAL = 500000000L; // 0.5 seconds
	/** Interval of playlist requests when a past request has failed, to avoid spamming. */
	private static final long FAILING_PLAYLIST_REQUEST_INTERVAL = 5L * 1000000000L;

    private static class Inner {

		private final DisplayLayerResources res;
		
        private final DisplayUrl url;
        private final DisplayTexture tex;
    
        private final MediaPlaylistParser hlsParser;
	
		private final ReadableProfiler profiler;
		
	    /** In nanoseconds monotonic, last fetch time. */
	    private long lastFetchTimestamp = 0;
	
	    // Playlist //

		/** The asynchronous processor */
		private final AsyncProcessor<URI, MediaPlaylist, IOException> asyncPlaylist;
	    /** Segments from the current playlist. */
	    private List<MediaSegment> playlistSegments;
	    /** Segment offset of the current playlist. */
	    private int playlistOffset;
	    /** Minimum timestamp for the next playlist request, only valid when in initial state. */
	    private long playlistNextRequestTimestamp;
		/** Current variable interval for playlist requests. */
		private long playlistRequestInterval;

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

		private final AsyncMap<URI, FrameGrabber, IOException> asyncGrabbers;
		
		// Sound //
		
		private final AudioStreamingSource audioSource;

		private Vec3i nearestAudioPos;
		private float nearestAudioDist;
		private float nearestAudioDistance;
		private float nearestAudioVolume;

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
			this.profiler = new ProfilerSystem(System::nanoTime, () -> 0, true);
			// this.profiler = DummyProfiler.INSTANCE;
	  
			this.asyncPlaylist = new AsyncProcessor<>(this::requestPlaylistBlocking, true);
			this.asyncGrabbers = new AsyncMap<>(this::requestGrabberBlocking, grabber -> {
				WebStreamerMod.LOGGER.info(makeLog("Stopping requested but unused grabber."));
				grabber.stop();
			}, GRABBER_REQUEST_TIMEOUT);
	        
	        this.audioSource = new AudioStreamingSource();

			this.resetPlaylist();
	
	        WebStreamerMod.LOGGER.info(makeLog("Allocate display layer for {}"), this.url);

        }

		private void free() {
			
			WebStreamerMod.LOGGER.info(makeLog("Free display layer for {}"), this.url);

			this.tex.clearGlId();
			this.asyncGrabbers.cleanup(this.res.getExecutor());
			this.audioSource.free();

		}
		
		private String makeLog(String message) {
			return String.format("[%08X] ", this.url.uri().hashCode()) + message;
		}
		
		// Audio //

		private void resetAudioSource() {
			if (this.nearestAudioPos != null) {
				this.audioSource.setPosition(this.nearestAudioPos);
				this.audioSource.setAttenuation(this.nearestAudioDistance);
				this.audioSource.setVolume(this.nearestAudioVolume);
			} else {
				this.audioSource.stop();
			}
			this.nearestAudioPos = null;
			this.nearestAudioDist = Float.MAX_VALUE;
			this.nearestAudioDistance = 0f;
			this.nearestAudioVolume = 0f;
		}

		private void pushAudioSource(Vec3i pos, float dist, float audioDistance, float audioVolume) {
			if (dist < this.nearestAudioDist) {
				this.nearestAudioPos = pos;
				this.nearestAudioDist = dist;
				this.nearestAudioDistance = audioDistance;
				this.nearestAudioVolume = audioVolume;
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
	
	    /** @return The last segment index for the current playlist. Special value of 0 if no segment. */
	    private int getLastSegmentIndex() {
		    return this.playlistSegments == null ? 0 : this.playlistSegments.size() - 1 + this.playlistOffset;
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
			this.playlistRequestInterval = INITIAL_PLAYLIST_REQUEST_INTERVAL;
		}
		
		/** Request the playlist if not already requesting and if this request is not pointless. */
		private void requestPlaylist(long now) {
			if (now >= this.playlistNextRequestTimestamp) {
				this.asyncPlaylist.push(this.url.uri());
				this.playlistNextRequestTimestamp = now + this.playlistRequestInterval;
			}
		}
	
		private void fetchPlaylist() {
			this.profiler.push("fetch_playlist");
			this.asyncPlaylist.fetch(this.res.getExecutor(), playlist -> {
				this.profiler.push("success");
				int newOffset = (int) playlist.mediaSequence();
				if (newOffset > this.playlistOffset) {
					this.playlistSegments = playlist.mediaSegments();
					this.playlistOffset = (int) playlist.mediaSequence();
					if (!this.playlistSegments.isEmpty()) {
						MediaSegment lastSegment = this.playlistSegments.get(this.playlistSegments.size() - 1);
						long newInterval = (long) (lastSegment.duration() * 1000000000.0 * 0.7);
						// Only change request interval if it represents more than 10% of the current interval.
						if (Math.abs(newInterval - this.playlistRequestInterval) >= this.playlistRequestInterval / 10) {
							WebStreamerMod.LOGGER.info(makeLog("New request interval: {}"), newInterval);
							this.playlistRequestInterval = newInterval;
						}
					}
				}
				this.profiler.pop();
			}, e -> {
				// If failing, put timestamp to retry later.
				this.playlistRequestInterval = FAILING_PLAYLIST_REQUEST_INTERVAL;
				WebStreamerMod.LOGGER.info(makeLog("Failed to request playlist, setting interval to {} seconds."), this.playlistRequestInterval / 1000000000, e);
			});
			this.profiler.pop();
		}
		
		// Grabber //

		private FrameGrabber requestGrabberBlocking(URI uri) throws IOException {
			FrameGrabber grabber = new FrameGrabber(this.res, uri);
			grabber.start();
			return grabber;
		}
	
	    /**
	     * Request a grabber at specific index.
	     * @param index The segment index of the grabber.
	     */
		private void requestGrabber(int index) {
			MediaSegment seg = this.getSegment(index);
			if (seg != null) {
				this.asyncGrabbers.push(this.res.getExecutor(), this.url.getContextUri(seg.uri()), index);
			}
		}

		/**
		 * Try to pull the given grabber, requested if not already. <b>The current grabber must be null.</b>
		 * @param index The segment index to pull.
		 */
		private void pullGrabberAndUse(int index) {
			boolean requested = this.asyncGrabbers.pull(index, grabber -> {
				this.grabber = grabber;
			}, e -> WebStreamerMod.LOGGER.error(makeLog("Failed to create and start grabber."), e));
			if (!requested) {
				this.requestGrabber(index);
			}
		}
	
	    /**
	     * Ensure that the current frame grabber get reset.
	     * @param toBeContinued True if a next grabber should directly follow the current one.
	     */
		private void resetGrabber(boolean toBeContinued) {
			if (this.grabber != null) {
				if (toBeContinued) {
					try {
						this.grabber.grabRemaining(this.audioSource::queueBuffer);
					} catch (IOException e) {
						WebStreamerMod.LOGGER.error(makeLog("Failed to grab remaining from current grabber."), e);
					}
				} else {
					this.audioSource.stop();
				}
				this.res.getExecutor().execute(this.grabber::stop);
				this.grabber = null;
			}
		}
	
        private void fetch() throws IOException {
			
            long now = System.nanoTime();
            double elapsedTime = ((double) (now - this.lastFetchTimestamp) / 1000000000.0);
            this.lastFetchTimestamp = now;
	
	        // System.out.println("sound source playing: " + this.soundSource.isPlaying());
			
			if (this.playlistSegments != null) {
	
				// Tries to pull the playlist if being requested.
	            this.fetchPlaylist();
				
				// This algorithm tries to go forward in segments by elapsedTime.
				double remainingTime = elapsedTime;
				
				// Request a playlist reset.
				boolean resetPlaylist = false;
				// Request a grabber reset, usually used when switching segment.
				boolean resetGrabber = false;
				
				for (;;) {
					
					// If we are too slow and the current segment is now out of the playlist.
					if (this.getCurrentSegment() == null) {
						WebStreamerMod.LOGGER.warn(makeLog("No current segment, reset playlist and grabber"));
						resetPlaylist = true;
						resetGrabber = true;
						break;
					}
					
					this.segmentTimestamp += remainingTime;
					if (this.segmentTimestamp > this.segmentDuration) {
						
						this.segmentIndex++;
						MediaSegment seg = this.getCurrentSegment();
						
						if (seg == null) {
							WebStreamerMod.LOGGER.warn(makeLog("No next segment, reset playlist and grabber"));
							resetPlaylist = true;
							resetGrabber = true;
							break;
						}
						
						resetGrabber = true;
						remainingTime = this.segmentTimestamp - this.segmentDuration;
						this.segmentDuration = seg.duration();
						this.segmentTimestamp = 0;
						
					} else {
						break;
					}
					
				}
				
				if (resetPlaylist) {
					this.resetPlaylist();
					this.resetGrabber(false);
				} else {
					
					if (resetGrabber) {
						// We only want to continue if the sound source is currently playing,
						// not playing means we are desynchronized.
						this.resetGrabber(true);
					}
					
					int offsetFromLastSegment = this.getLastSegmentIndex() - this.segmentIndex;
					
					if (offsetFromLastSegment <= 1) {
						// We are at most 1 segment from the end, so request a new playlist.
						this.requestPlaylist(now);
					}
					
					if (offsetFromLastSegment >= 1) {
						// If we have at least one segment after the current one, preload it.
						this.requestGrabber(this.segmentIndex + 1);
					}
					
				}
	
            }
	
	        if (this.playlistSegments == null) {

				if (this.asyncPlaylist.requested() || this.asyncPlaylist.active()) {
					// After request, we go here.
					this.fetchPlaylist();
					if (this.playlistSegments == null)
						return;
				} else {
					this.requestPlaylist(now);
					return;
				}
		
		        WebStreamerMod.LOGGER.info(makeLog("Initializing display layer... Found {} segments."), this.playlistSegments.size());
		
		        this.profiler.push("initialize_layer");
				
				double totalDuration = 0.0;
				for (MediaSegment seg : this.playlistSegments) {
			        totalDuration += seg.duration();
		        }
		
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
		
		        this.profiler.pop();
		
	        }
			
			// Grabbing and uploading section...
	  
			// If the grabber is in reset state, try to get it.
            if (this.grabber == null) {
				this.pullGrabberAndUse(this.segmentIndex);
				if (this.grabber == null) {
					// Abort if not ready to use.
					return;
				}
            }

			long segmentTimestampMicros = (long) (this.segmentTimestamp * 1000000);
	
	        this.profiler.push("grab_frame");
			Frame frame = this.grabber.grabAt(segmentTimestampMicros, this.audioSource::queueBuffer);
			this.profiler.pop();
			
			if (frame != null) {
				this.profiler.push("upload_image");
				this.tex.upload(frame);
				this.profiler.swap("play_audio");
				this.audioSource.playFrom(frame.timestamp);
				this.profiler.pop();
			}
			
        }

        private void tick() {
	
	        this.profiler.startTick();
	        this.profiler.push("tick");
			
	        try {
		        this.profiler.push("fetch");
				this.fetch();
	        } catch (IOException e) {
				WebStreamerMod.LOGGER.error(makeLog("Failed to fetch."), e);
	        } finally {
				this.profiler.pop();
	        }

			long now = System.nanoTime();
			boolean cleanup = now - this.lastCleanup >= CLEANUP_INTERVAL;
			if (cleanup) {
				this.profiler.push("cleanup");
				this.asyncGrabbers.cleanupTimedOut(this.res.getExecutor(), now);
				this.lastCleanup = now;
				this.profiler.pop();
			}
	
	        this.profiler.pop();
			this.profiler.endTick();
			
	        /*if (cleanup) {
		        ProfileResult res = this.profiler.getResult();
		        print(res, "root", 0);
	        }*/
	
        }
		
		/*private static void print(ProfileResult res, String path, int indent) {
			for (ProfilerTiming timing : res.getTimings(path)) {
				if (!timing.name.equals(path)) {
					StringBuilder builder = new StringBuilder();
					builder.append(" ".repeat(indent));
					builder.append("- ").append(timing.name).append(" x").append(timing.visitCount).append(" (").append(timing.totalUsagePercentage).append(" %/").append(timing.parentSectionUsagePercentage).append("%)");
					WebStreamerMod.LOGGER.info(builder.toString());
					print(res, path + "\u001e" + timing.name, indent + 2);
				}
			}
		}*/

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

	public void resetAudioSource() {
		this.inner.resetAudioSource();
	}

	public void pushAudioSource(Vec3i pos, float dist, float audioDistance, float audioVolume) {
		this.inner.pushAudioSource(pos, dist, audioDistance, audioVolume);
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
