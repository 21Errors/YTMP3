package com.example.musicplayer2.ui.album

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer2.MediaPlayerUtil.createTime
import com.example.musicplayer2.R
import com.example.musicplayer2.data.Song

class AlbumSongHolder(itemView: View, clickListener: AlbumSongAdapter.ItemClickListener) :
    RecyclerView.ViewHolder(itemView) {
    private val songTitleTextView: TextView
    private val songLengthTextView: TextView

    init {
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
        songTitleTextView = itemView.findViewById(R.id.textview_album_song_item_title)
        songLengthTextView = itemView.findViewById(R.id.textview_album_song_item_length)
    }

    fun bind(song: Song) {
        songTitleTextView.text = song.title
        songLengthTextView.text = createTime(song.extractDuration())
    }
}