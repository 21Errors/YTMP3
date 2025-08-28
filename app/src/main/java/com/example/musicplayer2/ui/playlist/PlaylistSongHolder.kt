package com.example.musicplayer2.ui.playlist

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer2.MediaPlayerUtil.createTime
import com.example.musicplayer2.R
import com.example.musicplayer2.data.Song

class PlaylistSongHolder(itemView: View, clickListener: PlaylistSongAdapter.ItemClickListener) :
    RecyclerView.ViewHolder(itemView) {
    private val songTitleTextview: TextView
    private val songLengthTextview: TextView

    init {
        songTitleTextview = itemView.findViewById(R.id.textview_playlist_song_item_title)
        songLengthTextview = itemView.findViewById(R.id.textview_playlist_song_item_length)
        itemView.setOnClickListener { view: View ->
            clickListener.onItemClick(
                bindingAdapterPosition, view
            )
        }
        itemView.setOnLongClickListener { view: View ->
            clickListener.onItemLongClick(
                bindingAdapterPosition, view
            )
        }
    }

    fun bind(song: Song) {
        songTitleTextview.text = song.title
        songLengthTextview.text = createTime(song.extractDuration())
    }
}