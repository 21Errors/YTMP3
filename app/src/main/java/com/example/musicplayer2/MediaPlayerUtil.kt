package com.example.musicplayer2

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import com.example.musicplayer2.data.Song
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.ui.main.MainActivity
import timber.log.Timber

object MediaPlayerUtil {
    private var mediaPlayer: MediaPlayer? = null
    private var fadeAnimator: ValueAnimator? = null
    private var originalVolume = 1.0f
    private var songStartTime: Long = 0L // Track when current song started playing

    // Threshold for smart back button (3 seconds)
    private const val RESTART_THRESHOLD_MS = 3000L

    @JvmStatic
    fun startPlaying(context: Context, song: Song, onPlaybackStarted: (() -> Unit)? = null): Boolean {
        Timber.i("Creating a new mediaPlayer to start playing: ${song.title}")

        val uri = Uri.fromFile(song.file)

        // Release old instance
        mediaPlayer?.let {
            Timber.i("mediaPlayer already exists, releasing...")
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing old MediaPlayer")
            }
        }

        try {
            // Create new MediaPlayer instance
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)

                // Set up completion listener before preparing
                setOnCompletionListener {
                    Timber.i("Song completed, playing next")
                    playNext(context)
                    if (context is MainActivity) {
                        context.onSongPlayingUpdate()
                    }
                }

                // Set up error listener
                setOnErrorListener { mp, what, extra ->
                    Timber.e("MediaPlayer error: what=$what, extra=$extra for song: ${song.title}")
                    // Try to play next song on error
                    playNext(context)
                    if (context is MainActivity) {
                        context.onSongPlayingUpdate()
                    }
                    true // Return true to indicate we handled the error
                }

                // Use async preparation for better performance
                setOnPreparedListener { mp ->
                    Timber.i("MediaPlayer prepared successfully, starting playback")
                    try {
                        mp.start()
                        songStartTime = System.currentTimeMillis()
                        Timber.i("Song started playing: ${song.title}")

                        // Notify UI that playback has actually started
                        onPlaybackStarted?.invoke()
                        if (context is MainActivity) {
                            Handler(Looper.getMainLooper()).post {
                                context.onSongPlayingUpdate()
                                context.onPlaybackUpdate()
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error starting playback after preparation")
                        // Try next song if this one fails to start
                        playNext(context)
                        if (context is MainActivity) {
                            context.onSongPlayingUpdate()
                        }
                    }
                }

                // Start async preparation
                prepareAsync()
            }

            return true

        } catch (e: Exception) {
            Timber.e(e, "Error creating MediaPlayer for song: ${song.title}")
            mediaPlayer?.release()
            mediaPlayer = null

            // Try to play next song if current one fails
            playNext(context)
            if (context is MainActivity) {
                context.onSongPlayingUpdate()
            }
            return false
        }
    }

    @JvmStatic
    fun playCurrent(context: Context) {
        SongsData.getInstance(context)?.songPlaying?.let {
            startPlaying(context, it) {
                // Callback when playback actually starts - just rely on existing MainActivity callbacks
                if (context is MainActivity) {
                    Handler(Looper.getMainLooper()).post {
                        context.onSongPlayingUpdate()
                        context.onPlaybackUpdate()
                    }
                }
            }
        }
    }

    @JvmStatic
    fun playNext(context: Context) {
        Timber.i("Retrieving next song in queue to play from songsData")
        val songsData = SongsData.getInstance(context) ?: return

        when {
            songsData.isRepeat -> { /* stay on same song */ }
            songsData.lastInQueue() -> songsData.playingIndex = 0
            else -> songsData.playNext()
        }

        Timber.i("Starting to play next song...")
        songsData.songPlaying?.let { startPlaying(context, it) }
    }

    @JvmStatic
    fun playPrev(context: Context) {
        Timber.i("Smart back button: checking if should restart or go to previous")
        val songsData = SongsData.getInstance(context) ?: return

        // Smart back button logic: restart if played > 5 seconds, otherwise go to previous
        val currentPosition = position
        val timePlayed = System.currentTimeMillis() - songStartTime

        if (currentPosition > RESTART_THRESHOLD_MS || timePlayed > RESTART_THRESHOLD_MS) {
            Timber.i("Song played for ${timePlayed}ms (position: ${currentPosition}ms), restarting current song")
            // Restart current song
            seekTo(0)
            songStartTime = System.currentTimeMillis() // Reset start time
            return
        }

        Timber.i("Song played for ${timePlayed}ms (position: ${currentPosition}ms), going to previous song")

        // Go to actual previous song
        when {
            songsData.isRepeat -> {
                // In repeat mode, restart current song
                seekTo(0)
                songStartTime = System.currentTimeMillis()
            }
            songsData.firstInQueue() -> {
                songsData.playingIndex = songsData.songsCount() - 1
                songsData.songPlaying?.let { startPlaying(context, it) }
            }
            else -> {
                songsData.playPrev()
                songsData.songPlaying?.let { startPlaying(context, it) }
            }
        }
    }

    @JvmStatic
    fun togglePlayPause() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    Timber.i("Toggling mediaPlayer from playing to pause")
                    it.pause()
                } else {
                    Timber.i("Toggling mediaPlayer from pause to playing")
                    it.start()
                    // Don't reset songStartTime when resuming, only when starting new song
                }
            } catch (e: IllegalStateException) {
                Timber.w(e, "togglePlayPause called but MediaPlayer not in valid state")
            }
        } ?: Timber.w("togglePlayPause called but mediaPlayer is null")
    }

    @JvmStatic
    fun play() {
        mediaPlayer?.let {
            try {
                if (!it.isPlaying) {
                    Timber.i("Starting mediaPlayer...")
                    it.start()
                    // Don't reset songStartTime when resuming
                }
            } catch (e: IllegalStateException) {
                Timber.w(e, "play() called but MediaPlayer not in valid state")
            }
        } ?: Timber.w("play() called but mediaPlayer is null")
    }

    @JvmStatic
    fun pause() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    Timber.i("Pausing mediaPlayer...")
                    it.pause()
                }
            } catch (e: IllegalStateException) {
                Timber.w(e, "pause() called but MediaPlayer not in valid state")
            }
        } ?: Timber.w("pause() called but mediaPlayer is null")
    }

    /**
     * Fades out the music over the specified duration, then stops playback
     * @param fadeDurationMs Duration of fade in milliseconds (default 5 seconds)
     * @param onFadeComplete Callback when fade is complete (optional)
     */
    fun fadeOutAndStop(
        fadeDurationMs: Long = 5000L,
        onFadeComplete: (() -> Unit)? = null
    ) {
        Timber.i("Starting fade out over ${fadeDurationMs}ms")

        // Cancel any existing fade
        fadeAnimator?.cancel()

        if (mediaPlayer == null || !isPlaying) {
            Timber.w("MediaPlayer is null or not playing, stopping immediately")
            stop()
            onFadeComplete?.invoke()
            return
        }

        try {
            // Store original volume
            originalVolume = 1.0f

            // Create fade animation
            fadeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
                duration = fadeDurationMs

                addUpdateListener { animator ->
                    val volume = animator.animatedValue as Float
                    try {
                        mediaPlayer?.setVolume(volume, volume)
                        Timber.d("Fade progress: ${(1.0f - volume) * 100}% (volume: $volume)")
                    } catch (e: Exception) {
                        Timber.e(e, "Error setting volume during fade")
                        animator.cancel()
                    }
                }

                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        Timber.i("Fade out animation started")
                    }

                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        Timber.i("Fade out animation completed - stopping playback")
                        // Restore volume before stopping (for next playback)
                        try {
                            mediaPlayer?.setVolume(originalVolume, originalVolume)
                        } catch (e: Exception) {
                            Timber.e(e, "Error restoring volume")
                        }
                        stop()
                        onFadeComplete?.invoke()
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        Timber.i("Fade out animation cancelled")
                        // Restore volume
                        try {
                            mediaPlayer?.setVolume(originalVolume, originalVolume)
                        } catch (e: Exception) {
                            Timber.e(e, "Error restoring volume after cancel")
                        }
                    }

                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })

                start()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error starting fade out animation")
            // Fallback to immediate stop
            stop()
            onFadeComplete?.invoke()
        }
    }



    @JvmStatic
    fun stop() {
        fadeAnimator?.cancel()
        songStartTime = 0L // Reset start time when stopping

        Timber.i("Stopping mediaPlayer")
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping MediaPlayer")
            }
        }
        mediaPlayer = null
    }

    val isStopped: Boolean
        get() = mediaPlayer == null

    val isPlaying: Boolean
        get() = try {
            val mp = mediaPlayer
            mp != null && mp.isPlaying
        } catch (e: IllegalStateException) {
            Timber.w("Error checking isPlaying state, MediaPlayer not ready")
            false
        } catch (e: Exception) {
            Timber.w("Unexpected error checking isPlaying state")
            false
        }

    @JvmStatic
    fun seekTo(pos: Int) {
        mediaPlayer?.let {
            try {
                it.seekTo(pos)
                Timber.d("Seeked to position: $pos")
            } catch (e: IllegalStateException) {
                Timber.w("Tried to seek but MediaPlayer not ready")
            }
        }
    }

    @JvmStatic
    val position: Int
        get() {
            val mp = mediaPlayer ?: return 0
            return try {
                mp.currentPosition
            } catch (e: IllegalStateException) {
                Timber.w("Tried to get position but MediaPlayer not ready")
                0
            }
        }

    @JvmStatic
    val duration: Int
        get() {
            val mp = mediaPlayer ?: return 0
            return try {
                mp.duration
            } catch (e: IllegalStateException) {
                Timber.w("Tried to get duration but MediaPlayer not ready")
                0
            }
        }

    @JvmStatic
    val audioSessionId: Int
        get() = try {
            mediaPlayer?.audioSessionId ?: 0
        } catch (e: IllegalStateException) {
            Timber.w("Error getting audioSessionId")
            0
        }

    @JvmStatic
    fun createTime(duration: Int): String {
        val safeDuration = if (duration < 0) 0 else duration
        val min = safeDuration / 1000 / 60
        val sec = safeDuration / 1000 % 60
        return String.format("%d:%02d", min, sec)
    }
}