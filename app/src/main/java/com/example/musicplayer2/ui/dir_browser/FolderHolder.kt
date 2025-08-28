package com.example.musicplayer2.ui.dir_browser

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox // Use standard CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.musicplayer2.R
import com.example.musicplayer2.ui.dir_browser.FolderAdapter.AdapterListeners
import java.io.File

class FolderHolder(
    inflater: LayoutInflater,
    parent: ViewGroup?,
    private val adapterListeners: AdapterListeners,
    private val folderCallBack: FolderCallBack
) : RecyclerView.ViewHolder(inflater.inflate(R.layout.list_item_dir_browser, parent, false)) {

    private val dirNameTextView: TextView = itemView.findViewById(R.id.textview_dir_item_name)
    val selectedCheckBox: CheckBox = itemView.findViewById(R.id.checkbox_dir_item_selected)
    var folder: File? = null

    init {
        itemView.setOnClickListener { adapterListeners.onItemClicked(this) }
        itemView.setOnLongClickListener { adapterListeners.onItemLongClicked(this) }
        selectedCheckBox.setOnClickListener { adapterListeners.onCheckBoxClicked(this) }
        (selectedCheckBox.parent as View).setOnClickListener { adapterListeners.onItemLongClicked(this) }
    }

    fun bind(folder: File) {
        this.folder = folder

        // Check if the folder is readable
        if (!folder.canRead() || folder.listFiles() == null) {
            dirNameTextView.setTextColor(Color.DKGRAY)
            selectedCheckBox.isEnabled = false
            return
        }

        selectedCheckBox.isEnabled = true
        setSelectedCheckBoxState()

        val name = folder.name
        dirNameTextView.text = if (folderCallBack.isAtRoot) {
            if (name == "0") itemView.context.getString(R.string.dir_browser_internal_storage)
            else itemView.context.getString(R.string.dir_browser_sd_card, name)
        } else {
            name
        }
    }

    private fun setSelectedCheckBoxState() {
        folder?.let { f ->
            // Use a simple if-else for two states
            if (folderCallBack.isFolderSelected(f)) {
                selectedCheckBox.isChecked = true
            } else {
                selectedCheckBox.isChecked = false
            }
        }
    }

    interface FolderCallBack {
        fun isFolderSelected(folder: File): Boolean
        fun isSubFolderSelected(folder: File): Boolean
        val isAtRoot: Boolean
    }
}