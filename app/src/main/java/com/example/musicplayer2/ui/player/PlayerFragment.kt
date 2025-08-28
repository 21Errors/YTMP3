package com.example.musicplayer2.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.example.musicplayer2.MediaPlayerUtil

import com.example.musicplayer2.MediaPlayerUtil.createTime
import com.example.musicplayer2.MediaPlayerUtil.duration
import com.example.musicplayer2.MediaPlayerUtil.isPlaying
import com.example.musicplayer2.MediaPlayerUtil.isStopped
import com.example.musicplayer2.MediaPlayerUtil.playCurrent
import com.example.musicplayer2.MediaPlayerUtil.playNext
import com.example.musicplayer2.MediaPlayerUtil.playPrev
import com.example.musicplayer2.MediaPlayerUtil.position
import com.example.musicplayer2.MediaPlayerUtil.seekTo

import com.example.musicplayer2.OpenMusicApp
import com.example.musicplayer2.R
import com.example.musicplayer2.custom_views.CustomViewPager2
import com.example.musicplayer2.data.Playlist
import com.example.musicplayer2.data.Song
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.Companion.getInstance
import com.example.musicplayer2.ui.sleeptime.SleepTimeActivity
import com.example.musicplayer2.utils.DialogUtils.OnNewPlaylistCallback
import com.example.musicplayer2.utils.DialogUtils.OnPlaylistUpdateCallback
import com.example.musicplayer2.utils.DialogUtils.showAddToPlaylistDialog
import timber.log.Timber
import java.util.*

class PlayerFragment : Fragment() {
    private var actionBar: ActionBar? = null
    private var playSongButton: Button? = null
    private var nextSongButton: Button? = null
    private var previousSongButton: Button? = null
    private var queueButton: Button? = null
    private var playlistButton: Button? = null
    private var repeatCheckBox: CheckBox? = null
    private var shuffleCheckBox: CheckBox? = null
    private var favoriteCheckBox: CheckBox? = null
    private var sleepTimerCheckBox: CheckBox? = null
    private var songPager: CustomViewPager2? = null
    private var songStartTimeTextview: TextView? = null
    private var songEndTimeTextview: TextView? = null
    private var songSeekBar: SeekBar? = null
    private var hostCallBack: Host? = null
    private var songsData: SongsData? = null
    private var songPlaying: Song? = null
    private var startPlaying = false
    private var currentlySeeking = false

    // Add visualizer related variables (simplified for ProgressBar)
    private var visualizerProgressBar: ProgressBar? = null
    private var isVisualizerActive = false

    companion object {
        private const val SLEEP_TIMER_REQUEST_CODE = 1001

        @JvmStatic
        fun newInstance(startPlaying: Boolean): PlayerFragment {
            val instance = PlayerFragment()
            instance.startPlaying = startPlaying
            return instance
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Initialize songsData with null check
            songsData = getInstance(requireContext())
            if (songsData == null) {
                // Log error or show user-friendly message
                return
            }

            // Get songPlaying with null check
            songPlaying = songsData?.songPlaying
            if (songPlaying == null) {
                // No song is currently playing - handle gracefully
                // You might want to show a message or navigate back
                return
            }

            // Get ActionBar with safe casting and null checks
            val activity = activity as? AppCompatActivity
            actionBar = activity?.supportActionBar

            // Safely set ActionBar properties
            actionBar?.apply {
                setDisplayHomeAsUpEnabled(false)
                title = songPlaying?.title ?: "Music Player"
            }

        } catch (e: Exception) {
            // Log the exception for debugging
            e.printStackTrace()
            // Handle the error gracefully - maybe finish the fragment or show error
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_player, container, false)

        // Initialize views safely
        songPager = CustomViewPager2(view.findViewById(R.id.viewpager_player_song))
        songPager?.disableNestedScrolling()

        previousSongButton = view.findViewById(R.id.button_player_prev)
        nextSongButton = view.findViewById(R.id.button_player_next)
        playSongButton = view.findViewById(R.id.button_player_play_pause)
        repeatCheckBox = view.findViewById(R.id.checkbox_player_repeat)
        shuffleCheckBox = view.findViewById(R.id.checkbox_player_shuffle)
        favoriteCheckBox = view.findViewById(R.id.checkbox_player_favorite)
        sleepTimerCheckBox = view.findViewById(R.id.checkbox_player_sleep_timer)
        queueButton = view.findViewById(R.id.button_player_queue)
        playlistButton = view.findViewById(R.id.button_player_addtoplaylist)

        songStartTimeTextview = view.findViewById(R.id.textview_player_elapsed_time)
        songEndTimeTextview = view.findViewById(R.id.textview_player_duration)
        songSeekBar = view.findViewById(R.id.seekbar_player)

        // Setup sleep timer checkbox
        setupSleepTimerCheckBox()

        // Setup pager adapter only if we have songs
        songsData?.getPlayingQueue()?.let { queue ->
            songPager?.get()?.adapter = SongPagerAdapter(requireContext(), queue)
            songPager?.get()?.setCurrentItem(songsData?.playingIndex ?: 0, false)

            songPager?.setOnPageChange(object : OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    songsData?.playingIndex = songPager?.get()?.currentItem ?: 0
                    playCurrent(requireContext())
                    hostCallBack?.onSongPlayingUpdate()
                }
            })
        }

        // Now safe to update UI
        updatePlayerUI()

        // Repeat & shuffle setup
        repeatCheckBox?.isChecked = songsData?.isRepeat ?: false
        shuffleCheckBox?.isChecked = songsData?.isShuffle ?: false

        queueButton?.setOnClickListener { hostCallBack?.showQueue() }
        playlistButton?.setOnClickListener {
            val song = songPlaying ?: return@setOnClickListener
            showAddToPlaylistDialog(
                requireContext(),
                song,
                object : OnNewPlaylistCallback {
                    override fun onNewPlaylist(newPlaylist: Playlist) {
                        hostCallBack?.onNewPlaylist(newPlaylist)
                    }
                },
                object : OnPlaylistUpdateCallback {
                    override fun onPlaylistUpdate(playlist: Playlist) {
                        if (playlist == songsData?.favoritesPlaylist) {
                            favoriteCheckBox?.isChecked =
                                songsData?.isFavorited(song) ?: false
                        }
                        hostCallBack?.onPlaylistUpdate(playlist)
                    }
                }
            )
        }

        // Seekbar updater thread (unchanged, but safe calls used)
        val songSeekBarUpdaterThread: Thread = object : Thread() {
            override fun run() {
                while (true) {
                    if (isResumed) {
                        try {
                            sleep(500)
                            if (!currentlySeeking) {
                                songSeekBar?.progress = position
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        songSeekBarUpdaterThread.start()

        songSeekBar?.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                currentlySeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                seekTo(seekBar.progress)
                songStartTimeTextview?.text = createTime(position)
                currentlySeeking = false
            }
        })

        // Timer to update elapsed time
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isStopped) {
                    songStartTimeTextview?.text = createTime(position)
                    handler.postDelayed(this, 1000)
                }
            }
        }, 1000)

        // Buttons
        playSongButton?.setOnClickListener { togglePlayPause() }

        nextSongButton?.setOnClickListener { playNextSong() }
        nextSongButton?.setOnLongClickListener {
            if (isPlaying) {
                seekTo(position + 10000)
                songStartTimeTextview?.text = createTime(position)
            }
            true
        }

        previousSongButton?.setOnClickListener { playPrevSong() }
        previousSongButton?.setOnLongClickListener {
            if (isPlaying) {
                seekTo(position - 10000)
                songStartTimeTextview?.text = createTime(position)
            }
            true
        }

        // Repeat/shuffle/favorite
        repeatCheckBox?.setOnCheckedChangeListener { _, isChecked ->
            songsData?.isRepeat = isChecked
        }

        shuffleCheckBox?.setOnCheckedChangeListener { _, isChecked ->
            songsData?.isShuffle = isChecked
            hostCallBack?.onShuffle()
        }

        favoriteCheckBox?.setOnClickListener { v ->
            val isChecked = (v as? CheckBox)?.isChecked ?: return@setOnClickListener
            val favorites = songsData?.favoritesPlaylist ?: return@setOnClickListener
            val song = songPlaying ?: return@setOnClickListener

            if (isChecked) songsData?.insertToPlaylist(favorites, song)
            else songsData?.removeFromPlaylist(favorites, song, favorites.songList.indexOf(song), true)

            hostCallBack?.onPlaylistUpdate(favorites)
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                hostCallBack?.onPlayerLoadComplete()
            }
        })

        // Visualizer
        initializeVisualizer()

        return view
    }

    private fun setupSleepTimerCheckBox() {
        sleepTimerCheckBox?.setOnClickListener {
            Timber.d("Sleep timer checkbox clicked")
            // Launch SleepTimeActivity
            val intent = Intent(requireContext(), SleepTimeActivity::class.java)
            startActivityForResult(intent, SLEEP_TIMER_REQUEST_CODE)
        }

        // Initialize sleep timer state
        updateSleepTimerState()
    }

    private fun updateSleepTimerState() {
        try {
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val isTimerEnabled = preferences.getBoolean(OpenMusicApp.PREFS_KEY_TIMEPICKER_SWITCH, false)

            if (isTimerEnabled) {
                // Check if timer is still valid (hasn't expired)
                val timerSeconds = preferences.getInt(OpenMusicApp.PREFS_KEY_TIMEPICKER, 0)
                val isStillActive = isSleepTimerStillActive(timerSeconds)

                sleepTimerCheckBox?.isChecked = isStillActive
                Timber.d("Sleep timer state updated: isEnabled=$isTimerEnabled, isActive=$isStillActive")
            } else {
                sleepTimerCheckBox?.isChecked = false
                Timber.d("Sleep timer disabled")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating sleep timer state")
            sleepTimerCheckBox?.isChecked = false
        }
    }

    private fun isSleepTimerStillActive(timerSeconds: Int): Boolean {
        try {
            val calendar = Calendar.getInstance()
            val currentTimeSeconds = calendar.get(Calendar.HOUR_OF_DAY) * 3600 + calendar.get(Calendar.MINUTE) * 60

            // Simple check - if current time hasn't reached timer time, it's still active
            // Note: This is a simplified check. For more accuracy, you'd need to store the actual target timestamp
            return if (timerSeconds > currentTimeSeconds) {
                // Timer is later today
                true
            } else {
                // Timer might be tomorrow, consider it active if the difference is reasonable
                (timerSeconds + 24 * 3600 - currentTimeSeconds) < 24 * 3600
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking sleep timer validity")
            return false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SLEEP_TIMER_REQUEST_CODE) {
            Timber.d("Received result from SleepTimeActivity: resultCode=$resultCode")

            // Update sleep timer state when returning from SleepTimeActivity
            updateSleepTimerState()

            // Optional: Show toast with timer status
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val isTimerEnabled = preferences.getBoolean(OpenMusicApp.PREFS_KEY_TIMEPICKER_SWITCH, false)

            if (isTimerEnabled) {
                val timerSeconds = preferences.getInt(OpenMusicApp.PREFS_KEY_TIMEPICKER, 0)
                val hours = timerSeconds / 3600
                val minutes = (timerSeconds % 3600) / 60
                Toast.makeText(
                    requireContext(),
                    "Sleep timer set for ${String.format("%02d:%02d", hours, minutes)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            hostCallBack = context as Host
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement PlayerFragment.Host")
        }
    }

    override fun onPause() {
        super.onPause()
        startPlaying = false
        releaseVisualizer()
    }

    override fun onResume() {
        updatePlayerUI()
        updateSleepTimerState() // Update sleep timer state when resuming
        initializeVisualizer()
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVisualizer()
    }

    private fun initializeVisualizer() {
        try {
            // Simple initialization for ProgressBar visualizer
            visualizerProgressBar?.let {
                it.max = 100
                it.progress = 50 // Default middle position
                isVisualizerActive = true

                // You can add animation or updates here based on audio if needed
                // For now, it's just a static progress bar as per your XML
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun releaseVisualizer() {
        try {
            // Simple cleanup for ProgressBar visualizer
            visualizerProgressBar?.let {
                it.progress = 0
                isVisualizerActive = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun invalidatePager() {
        val adapter = songPager!!.get().adapter as SongPagerAdapter
        adapter.setQueue(songsData?.getPlayingQueue()!!)
        songPager!!.get().adapter!!.notifyDataSetChanged()
        songPager!!.get().setCurrentItem(songsData!!.playingIndex, false)
    }

    fun updatePlayerUI() {
        val song = songsData?.songPlaying ?: return
        songPlaying = song

        // Immediate UI updates (non-MediaPlayer dependent)
        favoriteCheckBox?.isChecked = songsData?.isFavorited(song) ?: false
        actionBar?.title = song.title

        // Pager
        songPager?.get()?.let { pager ->
            if (pager.currentItem != (songsData?.playingIndex ?: 0)) {
                songPager?.scrollByCode(songsData?.playingIndex ?: 0, false)
            }
        }

        // REMOVED: All the Handler.postDelayed stuff - this was causing the flickering
        // Just do immediate updates, let the background threads handle the rest
        updatePlayButton()
        val pos = position
        val dur = duration
        songSeekBar?.max = dur
        songSeekBar?.progress = pos
        songEndTimeTextview?.text = createTime(dur)
        songStartTimeTextview?.text = createTime(pos)
    }

    fun updatePlayButton() {
        // Make this more defensive about MediaPlayer state
        val playing = try {
            isPlaying
        } catch (e: Exception) {
            false // Default to not playing if we can't determine state
        }

        if (playing) {
            playSongButton?.setBackgroundResource(R.drawable.ic_pause)
        } else {
            playSongButton?.setBackgroundResource(R.drawable.ic_play)
        }
    }

    private fun playNextSong() {
        playNext(requireContext())
        // REMOVED: The Handler.postDelayed - let the existing threads handle UI updates
        hostCallBack?.onSongPlayingUpdate()
    }

    private fun playPrevSong() {
        playPrev(requireContext())
        hostCallBack!!.onSongPlayingUpdate()
    }

    fun togglePlayPause() {
        MediaPlayerUtil.togglePlayPause()
        updatePlayButton()
        hostCallBack!!.onPlaybackUpdate()
    }

    interface Host {
        fun onPlayerLoadComplete()
        fun onPlaybackUpdate()
        fun onSongPlayingUpdate()
        fun onShuffle()
        fun onPlaylistUpdate(playlist: Playlist)
        fun showQueue()
        fun onNewPlaylist(newPlaylist: Playlist)
    }
}