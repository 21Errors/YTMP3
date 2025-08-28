package com.example.musicplayer2.data.base

import androidx.room.*
import com.example.musicplayer2.data.Playlist
import com.example.musicplayer2.data.Song

@Dao
interface PlaylistDao {
    @get:Query("SELECT * FROM Playlist")
    val all: MutableList<Playlist>

    @Insert
    fun insert(playlist: Playlist)

    @Query("SELECT song_id,song_path,album_id FROM Song NATURAL JOIN Playlist_Song WHERE Playlist_Song.playlist_id = :playlist_id ORDER BY song_index")
    fun getSongs(playlist_id: String): MutableList<Song>

    @Query("SELECT * FROM Playlist WHERE playlist_id=:playlist_id")
    operator fun get(playlist_id: String): Playlist

    @Update
    fun update(playlist: Playlist)

    @Delete
    fun delete(playlist: Playlist)
}