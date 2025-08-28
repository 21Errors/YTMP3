package com.example.musicplayer2.ui.queue

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.example.musicplayer2.data.Song
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.Companion.getInstance
import com.woxthebox.draglistview.DragItemAdapter

class ItemAdapter(
    private val context: Context,
    queue: List<Song>?,
    @LayoutRes layoutID: Int,
    @IdRes grabHandleID: Int,
    dragOnLongPress: Boolean
) : DragItemAdapter<Song, ItemHolder>() {

    private val songsData: SongsData? = getInstance(context)
    private val grabHandleID: Int
    private val dragOnLongPress: Boolean
    private val layoutID: Int
    private var playingHolder: ItemHolder? = null
    private var clickListener: OnItemClickedListener? = null

    init {
        this.layoutID = layoutID
        this.grabHandleID = grabHandleID
        this.dragOnLongPress = dragOnLongPress
        itemList = queue
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(layoutID, parent, false)
        return ItemHolder(context, view, grabHandleID, dragOnLongPress, clickListener!!)
    }

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val song = songsData!!.getSongFromQueueAt(position)
        holder.bind(song)
        if (holder.isPlaying) playingHolder = holder
    }

    fun setOnItemClickListener(clickListener: OnItemClickedListener) {
        this.clickListener = clickListener
    }

    override fun getUniqueItemId(position: Int): Long {
        val song = songsData!!.getSongFromQueueAt(position)
        return song.hashCode().toLong()
    }

    override fun getItemCount(): Int {
        return songsData!!.playingQueueCount
    }

    interface OnItemClickedListener {
        fun onItemClicked(position: Int)
    }
}
