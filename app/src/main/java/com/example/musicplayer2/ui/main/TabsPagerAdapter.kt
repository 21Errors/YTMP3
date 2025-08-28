package com.example.musicplayer2.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.musicplayer2.ui.albums_tab.AlbumsTabFragment
import com.example.musicplayer2.ui.all_songs.AllSongsFragment
import com.example.musicplayer2.ui.playlists_tab.PlaylistsTabFragment
import com.example.musicplayer2.ui.search.SearchFragment
import com.example.musicplayer2.ui.settings.SettingsFragment
import com.example.musicplayer2.YTConverterFragment

class TabsPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    override fun createFragment(position: Int): Fragment {
        val id = getItemId(position)
        return if (id == ALL_SONGS_TAB) AllSongsFragment()
        else (if (id == ALBUMS_TAB) AlbumsTabFragment()
        else if (id == PLAYLISTS_TAB) PlaylistsTabFragment()
        else if (id == YOUTUBE_CONVERTER_TAB) YTConverterFragment()
        else if (id == SETTINGS_TAB) SettingsFragment()
        else if (id == SEARCH_TAB) SearchFragment()

        else null)!!
    }

    override fun getItemCount(): Int {
        return TABS.size
    }

    override fun getItemId(position: Int): Long {
        return TABS[position]
    }

    override fun containsItem(itemId: Long): Boolean {
        for (id in TABS) if (id == itemId) return true
        return false
    }

    companion object {
        const val ALL_SONGS_TAB: Long = 720290723
        const val ALBUMS_TAB: Long = 885532984
        const val PLAYLISTS_TAB: Long = 851211671
        const val SETTINGS_TAB: Long = 736239367
        const val SEARCH_TAB: Long = 675435679
        const val YOUTUBE_CONVERTER_TAB: Long = 123456789  // Add this line

        val TABS = longArrayOf(ALL_SONGS_TAB, ALBUMS_TAB, PLAYLISTS_TAB, YOUTUBE_CONVERTER_TAB,SEARCH_TAB, SETTINGS_TAB)
    }
}