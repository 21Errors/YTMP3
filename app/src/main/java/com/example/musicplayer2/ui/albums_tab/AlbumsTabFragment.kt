package com.example.musicplayer2.ui.albums_tab

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
import androidx.recyclerview.widget.GridLayoutManager
import com.example.musicplayer2.R
import com.example.musicplayer2.custom_views.CustomRecyclerView
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.Companion.getInstance
import com.example.musicplayer2.ui.album.AlbumActivity

class AlbumsTabFragment : Fragment() {
    private var albumsRecyclerView: CustomRecyclerView? = null
    private var songsData: SongsData? = null
    private var hostCallback: Host? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        songsData = getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_albums_tab, container, false)
        albumsRecyclerView = view.findViewById(R.id.recyclerview_albums_tab_all)

        val albums = songsData?.allAlbums
        if (albums.isNullOrEmpty()) {
            // Optional: show empty view or return early
            return view
        }

        val adapter = AlbumsListAdapter(requireContext(), albums)
        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_CANCELED) hostCallback?.onQueueChanged()
        }

        adapter.setOnItemClickListener(object : AlbumsListAdapter.ItemClickListener {
            override fun onItemClick(position: Int, view: View) {
                val host = hostCallback
                val albumsList = songsData?.allAlbums

                if (host == null || albumsList == null || position !in albumsList.indices) return

                val intent = Intent(requireContext(), AlbumActivity::class.java).apply {
                    putExtra(AlbumActivity.EXTRA_ALBUM, albumsList[position])
                    putExtra(AlbumActivity.EXTRA_SHOW_PLAYER, host.isShowingPlayer)
                }
                host.onSongListClick()
                launcher.launch(intent)
            }

            override fun onItemLongClick(position: Int, view: View): Boolean {
                TODO("Not yet implemented")
            }
        })

        albumsRecyclerView?.adapter = adapter
        return view
    }


    fun invalidateAlbumList() {
        albumsRecyclerView!!.adapter!!.notifyDataSetChanged()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            hostCallback = context as Host
        } catch (e: ClassCastException) {
            throw ClassCastException(context.toString() + "must implement AlbumsTabFragment.Host")
        }
    }

    interface Host {
        val isShowingPlayer: Boolean
        fun onSongListClick()
        fun onQueueChanged()
    }
}