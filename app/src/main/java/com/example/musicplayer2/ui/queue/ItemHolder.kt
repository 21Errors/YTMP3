package com.example.musicplayer2.ui.queue

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.core.content.res.ResourcesCompat
import com.example.musicplayer2.R
import com.example.musicplayer2.data.Song
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.Companion.getInstance
import com.example.musicplayer2.ui.queue.ItemAdapter.OnItemClickedListener
import com.woxthebox.draglistview.DragItemAdapter

class ItemHolder(
    private val context: Context,
    itemView: View,
    @IdRes grabHandleID: Int,
    dragOnLongPress: Boolean,
    private val onItemClickedListener: OnItemClickedListener
) : DragItemAdapter.ViewHolder(itemView, grabHandleID, dragOnLongPress) {

    private val songsData: SongsData? = getInstance(context)
    private val songTitleTextView: TextView = itemView.findViewById(R.id.textview_queue_item_song_title)
    private val progressBar: ProgressBar? = itemView.findViewById(R.id.progress_bar_queue_item)
    private var song: Song? = null

    fun bind(song: Song) {
        this.song = song

        // Resolve theme color for text
        val typedValue = TypedValue()
        val theme = context.theme
        val color: Int = if (theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLACK
        }

        songTitleTextView.text = song.title

        if (bindingAdapterPosition < songsData!!.playingIndex) {
            songTitleTextView.setTextColor(Color.GRAY)
        } else {
            songTitleTextView.setTextColor(color)
        }

        if (isPlaying) {
            progressBar?.visibility = View.VISIBLE
            progressBar?.isIndeterminate = true
            progressBar?.background = null
        } else {
            progressBar?.visibility = View.GONE
            progressBar?.isIndeterminate = false
            progressBar?.background = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.ic_drag_handle,
                context.theme
            )
        }
    }

    val isPlaying: Boolean
        get() = song != null && song == songsData!!.songPlaying

    override fun onItemClicked(view: View) {
        super.onItemClicked(view)
        onItemClickedListener.onItemClicked(bindingAdapterPosition)
        songsData!!.songPlaying?.let { bind(it) }
    }
}
