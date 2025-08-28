package com.example.musicplayer2.ui.main

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.example.musicplayer2.OpenMusicApp
import com.example.musicplayer2.OpenMusicApp.Companion.hasPermissions
import com.example.musicplayer2.R
import com.example.musicplayer2.custom_views.SidenavMenu
import com.example.musicplayer2.data.Playlist
import com.example.musicplayer2.data.Song
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.LoadListener
import com.example.musicplayer2.ui.albums_tab.AlbumsTabFragment
import com.example.musicplayer2.ui.all_songs.AllSongsFragment
import com.example.musicplayer2.ui.all_songs.SongListAdapter
import com.example.musicplayer2.ui.player.PlayerFragment
import com.example.musicplayer2.ui.player_fragment_host.PlayerFragmentHost
import com.example.musicplayer2.ui.player_song_info.SonginfoFragment
import com.example.musicplayer2.ui.playlists_tab.PlaylistsTabFragment
import com.example.musicplayer2.ui.search.SearchFragment
import com.example.musicplayer2.ui.settings.SettingsFragment
import timber.log.Timber
import java.util.*
import kotlin.system.exitProcess

class MainActivity : PlayerFragmentHost(), AllSongsFragment.Host, AlbumsTabFragment.Host,
    PlaylistsTabFragment.Host, SettingsFragment.Host, SearchFragment.Host,
    ActivityResultCallback<ActivityResult>, LoadListener {
    private var songListAdapter: SongListAdapter? = null
    private var tabsPager: ViewPager2? = null
    private var tabsLayout: TabLayout? = null
    private var loadingSnackBar: Snackbar? = null
    private var prefs: SharedPreferences? = null
    private var sidenavmenu: SidenavMenu? = null
    private lateinit var closeAppReceiver: BroadcastReceiver

    /**
     * Gets executed every time the app starts
     *
     * @param savedInstanceState Android standard
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // UPDATED: Check if we should finish the app immediately
        if (intent.getBooleanExtra("FINISH_APP", false)) {
            Timber.i("MainActivity started with FINISH_APP flag - closing app")
            finishAffinity() // This closes all activities in the task
            return
        }

        // Choose layout based on menu switch preference
        val layoutRes = if (prefs!!.getBoolean(OpenMusicApp.PREFS_KEY_MENUSWITCH, false)) {
            R.layout.content_main2
        } else {
            R.layout.content_main
        }

        // Inflate layout without a parent
        val childView = layoutInflater.inflate(layoutRes, null)
        super.attachContentView(childView)

        // Initialize views using the inflated layout
        tabsPager = childView.findViewById(R.id.viewpager_main_tabs)
        tabsLayout = childView.findViewById(R.id.tab_layout_main)
        sidenavmenu = childView.findViewById(R.id.sidenavmenu)

        // Set up ViewPager page change callback
        tabsPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                sidenavmenu?.setSelection(position)
            }
        })

        // Set up Sidenav menu
        sidenavmenu?.setPager(tabsPager!!)

        // Set up ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // UPDATED: Register close app broadcast receiver
        setupCloseAppReceiver()

        // Register activity result callback
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), this)

        // Check runtime permissions
        runtimePermission()

        // REMOVED: Old sleep timer - now handled by SleepTimeFragment
        // startSleeptimer() // <-- Remove this line

        // Initialize song list adapter
        songListAdapter = SongListAdapter(this, SongsData.data?.getAllSongs() as MutableList<Song>)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupCloseAppReceiver() {
        closeAppReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.musicplayer2.CLOSE_APP" -> {
                        Timber.i("Received CLOSE_APP broadcast - finishing activity")
                        finishAffinity() // This closes all activities in the task
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.musicplayer2.CLOSE_APP")
        }

        // Fix registerReceiver based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeAppReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeAppReceiver, filter, RECEIVER_NOT_EXPORTED)
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Check if this is a close app intent
        if (intent.getBooleanExtra("FINISH_APP", false)) {
            Timber.i("MainActivity received new intent with FINISH_APP flag - closing app")
            finishAffinity()
        }
    }

    // 4. ADD onDestroy method to unregister receiver (you're missing this)
    override fun onDestroy() {
        try {
            unregisterReceiver(closeAppReceiver)
        } catch (e: Exception) {
            Timber.w(e, "Error unregistering close app receiver")
        }
        super.onDestroy()
    }


    /**
     * Creates the option menu you can see in the upper right corner (three dots)
     *
     * @param menu The menu to be created
     * @return The finished created menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN || bottomSheetBehavior?.state == BottomSheetBehavior.STATE_COLLAPSED) {
            menuInflater.inflate(R.menu.main, menu)
            val menuItem = menu.findItem(R.id.app_menu)
            val settingsItem = menu.findItem(R.id.app_settings)
            menuItem.isVisible = prefs!!.getBoolean(OpenMusicApp.PREFS_KEY_MENUSWITCH, false)
            settingsItem.isVisible = !prefs!!.getBoolean(OpenMusicApp.PREFS_KEY_MENUSWITCH, false)
            menuItem.setOnMenuItemClickListener {
                // This controls whetever the sidemenu is visible or not and changes accordingly
                if (sidenavmenu!!.visibility == View.VISIBLE) {
                    sidenavmenu!!.visibility = View.GONE
                } else {
                    sidenavmenu!!.visibility = View.VISIBLE
                }
                true
            }
            settingsItem.setOnMenuItemClickListener {
                tabsPager!!.setCurrentItem(4, true)
                true
            }
        } else if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
            menuInflater.inflate(R.menu.playing, menu)
            val songItem = menu.findItem(R.id.song_info_button)
            songItem.setOnMenuItemClickListener {
                // Checks what the current Fragment is and replaces it with the Songinfo or Player
                val fragment = supportFragmentManager.findFragmentById(R.id.layout_player_container)
                val fragmentTransaction = supportFragmentManager.beginTransaction()
                if (fragment != null && fragment.javaClass.toString() == PlayerFragment::class.java.toString()) {
                    fragmentTransaction.replace(R.id.layout_player_container, SonginfoFragment())
                } else {
                    fragmentTransaction.replace(R.id.layout_player_container, PlayerFragment())
                }
                fragmentTransaction.commit()
                true
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Checks for all required permissions
     * For musicplayer storage permission to find all the songs and record permission for the visualizer
     */
    private fun runtimePermission() {
        if (!hasPermissions(this@MainActivity)) {
            loadingSnackBar = Snackbar.make(
                rootView,
                R.string.all_loading_library,
                Snackbar.LENGTH_INDEFINITE
            )
        }
        Dexter.withContext(this).withPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(multiplePermissionsReport: MultiplePermissionsReport) {
                    try {
                        songsData?.loadFromDatabase(this@MainActivity)?.join()
                        if (loadingSnackBar != null) loadingSnackBar!!.show()
                        songsData?.loadFromFiles(this@MainActivity)
                        finishLoading()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                fun finishLoading() {
                    // Display all the songs
                    tabsPager!!.adapter = TabsPagerAdapter(this@MainActivity)
                    TabLayoutMediator(
                        tabsLayout!!,
                        tabsPager!!
                    ) { tab: TabLayout.Tab, position: Int ->
                        tab.text = resources.getStringArray(R.array.main_tabs)[position]
                    }
                        .attach()
                }

                override fun onPermissionRationaleShouldBeShown(
                    list: List<PermissionRequest>,
                    permissionToken: PermissionToken
                ) {
                    // Ask again and again until permissions are accepted
                    permissionToken.continuePermissionRequest()
                }
            }).check()
    }

    override fun onPlaylistUpdate(playlist: Playlist) {
        val index = songsData?.allPlaylists?.indexOf(playlist) ?: -1
        if (index >= 0) {
            val playlistsTab = getTabFragment(TabsPagerAdapter.PLAYLISTS_TAB) as? PlaylistsTabFragment
            playlistsTab?.updatePlaylistAt(index)
        }
    }

    override fun onNewPlaylist(newPlaylist: Playlist) {
        val playlistsTab = getTabFragment(TabsPagerAdapter.PLAYLISTS_TAB) as? PlaylistsTabFragment
        playlistsTab?.notifyPlaylistInserted()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            songsData?.loadFromDatabase(this)?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        tabsPager!!.adapter = TabsPagerAdapter(this)
    }

    override val isShowingPlayer: Boolean
        get() {
            // Check if the bottom sheet player is expanded or expanded/half-expanded
            return when (bottomSheetBehavior?.state) {
                BottomSheetBehavior.STATE_EXPANDED,
                BottomSheetBehavior.STATE_HALF_EXPANDED -> true
                else -> false
            }
        }

    override fun onSongListClick() {
        super.unregisterMediaReceiver()
    }

    override fun onLibraryDirsChanged() {
        loadingSnackBar = Snackbar.make(
            rootView,
            R.string.all_reloading_library,
            Snackbar.LENGTH_INDEFINITE
        )
        loadingSnackBar!!.show()
        songsData?.loadFromFiles(this)
    }

    private fun getTabFragment(tabId: Long): Fragment? {
        return supportFragmentManager.findFragmentByTag("f$tabId")
    }

    override fun onRemovedSongs() {
        val allSongsTab = getTabFragment(TabsPagerAdapter.ALL_SONGS_TAB) as? AllSongsFragment
        allSongsTab?.invalidateSongList()
    }

    override fun onAddedSongs() {
        val allSongsTab = getTabFragment(TabsPagerAdapter.ALL_SONGS_TAB) as? AllSongsFragment
        allSongsTab?.invalidateSongList()
    }

    override fun onAddedAlbums() {
        val albumsTab = getTabFragment(TabsPagerAdapter.ALBUMS_TAB) as? AlbumsTabFragment
        albumsTab?.invalidateAlbumList()
    }

    override fun onRemovedAlbums() {
        val albumsTab = getTabFragment(TabsPagerAdapter.ALBUMS_TAB) as? AlbumsTabFragment
        albumsTab?.invalidateAlbumList()
    }

    override fun onLoadComplete() {
        if (loadingSnackBar != null) loadingSnackBar!!.dismiss()
        songsData?.isDoneLoading = true
    }

    private fun startSleeptimer() {
        // Retrieves the time set in the Timepicker
        val settime = prefs!!.getInt(OpenMusicApp.PREFS_KEY_TIMEPICKER, 36480)
        val calendar = Calendar.getInstance()
        // Retrieves current time and converts it to seconds
        val currentTimes = calendar[Calendar.HOUR_OF_DAY] * 3600 + calendar[Calendar.MINUTE] * 60
        // Starts a thread to check for the sleep time to go off
        if (settime > currentTimes && prefs!!.getBoolean(
                OpenMusicApp.PREFS_KEY_TIMEPICKER_SWITCH,
                false
            )
        ) {
            val thread: Thread = object : Thread() {
                override fun run() {
                    // If the current time becomes the time set in the preference it exits the loop
                    while (true) {
                        val customCalendar = Calendar.getInstance()
                        val currentTime =
                            customCalendar[Calendar.HOUR_OF_DAY] * 3600 + customCalendar[Calendar.MINUTE] * 60
                        if (settime - currentTime <= 0) break
                    }
                    // Exit the app and shutdown
                    val manager =
                        applicationContext.getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    for (task in manager.appTasks) {
                        task.finishAndRemoveTask()
                        exitProcess(0)
                    }
                }
            }
            thread.start()
        } else {
            Timber.e("Thread not started :/")
        }
    }

    override fun onActivityResult(result: ActivityResult) {
        TODO("Not yet implemented")
    }
}