package com.example.musicplayer2.utils

import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import com.example.musicplayer2.data.Playlist
import timber.log.Timber
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

/**
 * Class to export and import
 * settings, playlistDB, etc...
 * WARNING! Is not accessible by the user right now because there is no
 * button or something for this yet.
 */
class ImportExportUtils {
    // Use it to export and import files to a given path
    // Example: openmusic.sqlite3, preferences.xml, etc...
    // WARNING! Replaces existing files
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun exportImportFile(source: Path, destination: Path) {
        try {
            // create stream for `source`
            val files = Files.walk(source)

            // copy all files and folders from `source` to `destination`
            files.forEach { file: Path? ->
                try {
                    Files.copy(
                        file, destination.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            // close the stream
            files.close()
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }

    /**
     * Extracts data from a given Playlist and writes it to a temporary file for later exportation
     * The data is written in m3u format!
     *
     * @param m3uFile       File to write data to
     * @param playlist_id   UUID of the playlist
     * @param playlist_name Name of the playlist
     * @return Filled file
     */
    private fun m3uConverter(m3uFile: File, playlist_id: UUID, playlist_name: String): File {
        val playlist = Playlist(playlist_id, playlist_name)
        val songList = playlist.songList
        try {
            val writer = FileWriter(m3uFile)
            writer.write("#EXTM3U\n\n")
            for (s in songList) {
                writer.write("#EXTINF:")
                writer.write(songList.indexOf(s).toString() + ", ")
                writer.write(
                    """
    ${s.title}
    
    """.trimIndent()
                )
                writer.write(s.path)
                writer.write("\n\n")
            }
            writer.close()
        } catch (e: IOException) {
            Timber.e("M3U-Error: %s", e.message)
        }
        return m3uFile
    }

    /**
     * Converts a given playlist in M3U format and exports it to a file
     *
     * @param export_location Where the m3u file should be exported to
     * @param playlist_id     UUID of the playlist
     * @param playlist_name   Name of the playlist
     */
    fun exportM3UPlaylist(export_location: Path, playlist_id: UUID, playlist_name: String) {
        var temporary = File(
            Environment.getDataDirectory().toString() + "/data/com.example.musicplayer2/files",
            "temporary.m3u"
        )
        temporary = m3uConverter(temporary, playlist_id, playlist_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            exportImportFile(temporary.toPath(), export_location)
        } else {
            Timber.e("Version too low for export")
        }
        temporary.delete()
    }

}