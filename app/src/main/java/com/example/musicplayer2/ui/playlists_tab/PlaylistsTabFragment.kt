package com.example.musicplayer2.ui.playlists_tab

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.musicplayer2.R
import com.example.musicplayer2.custom_views.CustomRecyclerView
import com.example.musicplayer2.data.Playlist
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.Companion.getInstance
import com.example.musicplayer2.ui.playlist.PlaylistActivity
import com.example.musicplayer2.utils.DialogUtils.OnNewPlaylistCallback
import com.example.musicplayer2.utils.DialogUtils.OnPlaylistDeleteCallback
import com.example.musicplayer2.utils.DialogUtils.showDeletePlaylistDialog
import com.example.musicplayer2.utils.DialogUtils.showNewPlaylistDialog

class PlaylistsTabFragment : Fragment() {
    private var songsData: SongsData? = null
    private var playlistsRecyclerView: CustomRecyclerView? = null
    private var playlistsAdapter: PlaylistsAdapter? = null
    private var hostCallback: Host? = null
    private var fabMain: FloatingActionButton? = null
    private var fabDelete: FloatingActionButton? = null
    private var fabCreate: FloatingActionButton? = null
    private var areSubfabsVisible = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        songsData = getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_playlists_tab, container, false)
        playlistsRecyclerView = view.findViewById(R.id.recyclerview_playlists_tab_playlists)
        playlistsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        playlistsAdapter = PlaylistsAdapter(requireContext(), songsData!!.allPlaylists!!)
        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult -> if (result.resultCode != Activity.RESULT_CANCELED) hostCallback!!.onQueueChanged() }
        playlistsAdapter!!.setOnItemClickListener(object : PlaylistsAdapter.ItemClickListener {
            override fun onItemClick(position: Int, view: View) {
                val playlists = songsData?.allPlaylists
                val host = hostCallback

                // If playlists or hostCallback is null, do nothing
                if (playlists == null || host == null || position !in playlists.indices) return

                val playlist = playlists[position]
                val intent = Intent(requireContext(), PlaylistActivity::class.java).apply {
                    putExtra(PlaylistActivity.EXTRA_PLAYLIST, playlist)
                    putExtra(PlaylistActivity.EXTRA_SHOW_PLAYER, host.isShowingPlayer)
                }

                host.onSongListClick()
                launcher.launch(intent)
            }


            override fun onItemLongClick(position: Int, view: View): Boolean {
                return false
            }
        })
        playlistsRecyclerView?.adapter = playlistsAdapter
        val emptyTextView = view.findViewById<TextView>(R.id.textview_playlists_tab_empty)
        playlistsRecyclerView?.setEmptyView(emptyTextView)

        // Manages the floating action button (fab)
        fabMain = view.findViewById(R.id.main_fab)
        fabCreate = view.findViewById(R.id.child_fab_add)
        fabDelete = view.findViewById(R.id.child_fab_remove)
        fabCreate?.visibility = View.GONE
        fabDelete?.visibility = View.GONE
        areSubfabsVisible = false

        // FAB click listener
        fabMain?.setOnClickListener {
            areSubfabsVisible = if (!areSubfabsVisible) {
                fabCreate?.show()
                fabDelete?.show()
                true
            } else {
                fabCreate?.hide()
                fabDelete?.hide()
                false
            }
        }
        fabCreate?.setOnClickListener {
            showNewPlaylistDialog(
                requireContext(),
                object : OnNewPlaylistCallback {
                    override fun onNewPlaylist(newPlaylist: Playlist) {
                        notifyPlaylistInserted()
                    }
                })
        }
        fabDelete?.setOnClickListener {
            showDeletePlaylistDialog(
                requireContext(),
                object : OnPlaylistDeleteCallback {
                    override fun onPlaylistDelete(index: Int) {
                        notifyPlaylistDeleted(index)
                    }
                })
        }
        return view
    }

    fun updatePlaylistAt(position: Int) {
        playlistsAdapter!!.notifyItemChanged(position)
    }

    fun notifyPlaylistInserted() {
        playlistsAdapter!!.notifyItemInserted(playlistsAdapter!!.itemCount)
    }

    private fun notifyPlaylistDeleted(index: Int) {
        playlistsAdapter!!.notifyItemRemoved(index)
    }

    override fun onAttach(context: Context) {
        hostCallback = try {
            context as Host
        } catch (exception: ClassCastException) {
            throw ClassCastException("$context must implement PlaylistsTabFragment.Host")
        }
        super.onAttach(context)
    }

    interface Host {
        val isShowingPlayer: Boolean
        fun onQueueChanged()
        fun onSongListClick()
    }
}