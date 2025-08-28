package com.example.musicplayer2.data.base

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.musicplayer2.data.Album
import com.example.musicplayer2.data.Song

@Dao
interface AlbumDao {
    @Insert
    fun insert(album: Album)

    @get:Query("SELECT * FROM ALBUM")
    val all: MutableList<Album>

    @Query("SELECT * FROM Song WHERE Song.album_id=:albumId")
    fun getSongs(albumId: String): MutableList<Song>

    @Delete
    fun delete(album: Album)
}