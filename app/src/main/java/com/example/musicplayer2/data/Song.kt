package com.example.musicplayer2.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.room.*
import androidx.room.ForeignKey.Companion.SET_NULL
import java.io.File
import java.io.Serializable
import java.util.*

@Entity(
    foreignKeys = [ForeignKey(
        entity = Album::class,
        parentColumns = arrayOf("album_id"),
        childColumns = arrayOf("album_id"),
        onDelete = SET_NULL
    )]
)
class Song
/**
 * Second custom constructor for the Song class. Creates a Song with file and title.
 *
 * @param songId ID of the song
 * @param file   File to create a song from
 * @param title  Name of the song
 */ @Ignore constructor(
    @field:ColumnInfo(
        name = "song_id",
        index = true
    ) @field:PrimaryKey var songId: UUID,
    /**
     * Sets the file of a song manually without constructor
     */
    @field:ColumnInfo(name = "song_path") var file: File,
    /**
     * Returns the title/name of the song
     *
     * @return name of the song
     */
    @field:Ignore val title: String
) : Serializable {

    /**
     * Returns the song as a file
     *
     * @return The file of the song
     */

    @ColumnInfo(name = "album_id", index = true)
    var albumID: UUID? = null

    @Ignore
    var album: Album? = null

    /**
     * First custom constructor for the Song class. Creates a Song with file only.
     *
     * @param file   File to create a song from
     * @param songId ID of the song
     */
    constructor(songId: UUID, file: File) : this(
        songId,
        file,
        file.name.replace("(?<!^)[.][^.]*$".toRegex(), "")
    ) {
        // removes invalid characters from the filename before creating a song out of it
    }

    val path: String
        get() = file.absolutePath

    override fun equals(other: Any?): Boolean {
        return if (other !is Song) false else other.songId == songId
    }

    fun extractDuration(): Int {
        val duration = metaDataReceiver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        return duration?.toIntOrNull() ?: 0
    }

    private val folderName: String
        get() {
            val folders = path.split("/").toTypedArray()
            return if (folders.size >= 2) folders[folders.size - 2] else "Unknown"
        }

    fun extractAlbumTitle(): String {
        val albumTitle = metaDataReceiver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        return albumTitle ?: "Unknown Album"
    }

    fun extractAlbumArt(): Bitmap? {
        return try {
            val art = metaDataReceiver.embeddedPicture
            if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun extractArtists(): String {
        val artist = metaDataReceiver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        return artist ?: "Unknown Artist"
    }

    override fun hashCode(): Int {
        var result = songId.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (albumID?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + path.hashCode()
        result = 31 * result + folderName.hashCode()
        return result
    }

    private val metaDataReceiver: MediaMetadataRetriever
        get() {
            val metadataRetriever = MediaMetadataRetriever()
            try {
                metadataRetriever.setDataSource(path)
            } catch (e: Exception) {
                // Handle case where file might be corrupted or inaccessible
                // Return a safe metadata retriever or handle gracefully
            }
            return metadataRetriever
        }
}