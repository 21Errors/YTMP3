package com.example.musicplayer2

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.musicplayer2.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class YTConverterFragment : Fragment() {

    private lateinit var youtubeUrlInput: EditText
    private lateinit var convertButton: Button
    private lateinit var openPlaylistConverterButton: Button
    private lateinit var statusText: TextView
    private val STORAGE_PERMISSION_CODE = 1001
    private var lastCommandOutput = ""

    // Add coroutine scope for managing background tasks
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    // Custom DownloaderImpl class
    class DownloaderImpl : Downloader() {
        companion object {
            private var instance: DownloaderImpl? = null

            fun init(): DownloaderImpl {
                instance = DownloaderImpl()
                return instance!!
            }

            fun getInstance(): DownloaderImpl? {
                return instance
            }
        }

        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
            // Simple implementation using basic HTTP connection
            val url = java.net.URL(request.url())
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.requestMethod = request.httpMethod()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            // Add headers
            request.headers().forEach { (key, values) ->
                values.forEach { value ->
                    connection.setRequestProperty(key, value)
                }
            }

            // Handle request body if present
            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: ""
            val responseHeaders = connection.headerFields
            val responseBody = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            return org.schabi.newpipe.extractor.downloader.Response(
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                request.url()
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_main, container, false)

        youtubeUrlInput = view.findViewById(R.id.youtubeUrlInput)
        convertButton = view.findViewById(R.id.convertButton)
        openPlaylistConverterButton = view.findViewById(R.id.openPlaylistConverterButton)
        statusText = view.findViewById(R.id.statusText)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // FFmpeg log callback
        Config.enableLogCallback { message ->
            lastCommandOutput += message.text + "\n"
            activity?.runOnUiThread {
                if (message.text.contains("error", true) ||
                    message.text.contains("failed", true) ||
                    message.text.contains("invalid", true)
                ) {
                    statusText.text = "Status: ${message.text}"
                }
            }
        }

        convertButton.setOnClickListener {
            if (checkStoragePermissions()) {
                processUrl()
            } else {
                requestStoragePermissions()
            }
        }

        openPlaylistConverterButton.setOnClickListener {
            // Since we're in a Fragment, we need to start the PlaylistConverterActivity
            val intent = android.content.Intent(requireContext(), com.example.musicplayer2.PlaylistConverterActivity::class.java)
            startActivity(intent)
        }

        statusText.text = "Status: Ready. Enter a YouTube URL and click Convert."
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - only need READ_MEDIA_AUDIO for reading, but we also need to write
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 - Scoped storage, don't need WRITE_EXTERNAL_STORAGE for MediaStore
            true // MediaStore operations don't require explicit permissions on Android 10+
        } else {
            // Android 9 and below
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we might not need to request permissions for MediaStore
            // But request anyway to be safe
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        ActivityCompat.requestPermissions(requireActivity(), permissions, STORAGE_PERMISSION_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                processUrl()
            } else {
                Toast.makeText(context, "Storage permissions denied", Toast.LENGTH_SHORT).show()
                statusText.text = "Status: Permissions denied"
            }
        }
    }

    private fun processUrl() {
        val url = youtubeUrlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }

        when {
            url.contains("youtube.com") || url.contains("youtu.be") -> {
                statusText.text = "Status: Extracting YouTube URL..."
                extractYouTubeUrl(url)
            }
            url.startsWith("http") -> {
                statusText.text = "Status: Processing direct URL..."
                convertToMp3(url, "DirectAudio")
            }
            else -> {
                Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                statusText.text = "Status: Invalid URL format"
            }
        }
    }

    private fun extractYouTubeUrl(youtubeUrl: String) {
        // Use proper coroutines instead of runBlocking
        mainScope.launch {
            try {
                statusText.text = "Status: Initializing extractor..."

                // Do network operations on IO dispatcher
                val result = withContext(Dispatchers.IO) {
                    // Initialize the downloader if not already done
                    if (DownloaderImpl.getInstance() == null) {
                        DownloaderImpl.init()
                    }

                    NewPipe.init(DownloaderImpl.getInstance())
                    val streamInfo = StreamInfo.getInfo(youtubeUrl)
                    streamInfo.audioStreams
                }

                // Back on main thread to update UI
                if (result.isNotEmpty()) {
                    val bestAudio = result.maxByOrNull { it.bitrate }
                    if (bestAudio != null) {
                        val streamInfo = withContext(Dispatchers.IO) {
                            StreamInfo.getInfo(youtubeUrl)
                        }
                        val title = streamInfo.name ?: "Unknown"
                        statusText.text = "Status: Found \"$title\", converting..."
                        Log.d("NewPipe", "Extracting: $title")
                        Log.d("NewPipe", "Audio URL: ${bestAudio.url}")
                        bestAudio.url?.let { convertToMp3(it, title) }
                    } else {
                        statusText.text = "Status: No audio stream found"
                        Toast.makeText(
                            context,
                            "No audio stream found in this video",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("NewPipe", "No suitable audio stream found for URL: $youtubeUrl")
                    }
                } else {
                    statusText.text = "Status: Failed to extract YouTube URL"
                    Toast.makeText(
                        context,
                        "Failed to extract YouTube URL. Video might be private, restricted, or unsupported.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("NewPipe", "No audio streams returned for URL: $youtubeUrl")
                }
            } catch (e: Exception) {
                statusText.text = "Status: Extraction error"
                Toast.makeText(
                    context,
                    "Extraction failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("NewPipe", "Extraction error for URL: $youtubeUrl", e)
            }
        }
    }

    private fun convertToMp3(url: String, title: String? = null) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val cleanTitle = title?.let {
            it.replace(Regex("[^A-Za-z0-9 \\-_]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(50)
        }
        val fileName = if (!cleanTitle.isNullOrEmpty()) "${cleanTitle}_$timeStamp" else "Audio_$timeStamp"

        val resolver = requireActivity().contentResolver
        var outputUri: android.net.Uri? = null
        var tempFile: File? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : insert into MediaStore first
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.mp3")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                outputUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (outputUri == null) {
                    Toast.makeText(context, "❌ Failed to create MediaStore entry", Toast.LENGTH_LONG).show()
                    statusText.text = "Status: ❌ Failed to create MediaStore entry"
                    return
                }

                // Use temporary file to run FFmpeg
                tempFile = File(requireContext().cacheDir, "$fileName.mp3")
            } else {
                // Android 9 and below : public Music folder
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                musicDir.mkdirs()
                tempFile = File(musicDir, "$fileName.mp3")
            }

            lastCommandOutput = ""
            val command = arrayOf(
                "-i", url,
                "-vn",
                "-acodec", "libmp3lame",
                "-ab", "192k",
                "-ar", "44100",
                "-y",
                tempFile.absolutePath
            )

            statusText.text = "Status: Converting to MP3..."

            mainScope.launch {
                val rc = withContext(Dispatchers.IO) { FFmpeg.execute(command) }

                if (rc == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outputUri != null) {
                        // Copy temp file to MediaStore
                        resolver.openOutputStream(outputUri).use { out ->
                            tempFile.inputStream().use { it.copyTo(out!!) }
                        }

                        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                        resolver.update(outputUri, values, null, null)
                        tempFile.delete()
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // Notify media scanner
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(tempFile.absolutePath),
                            arrayOf("audio/mpeg"),
                            null
                        )
                    }

                    Toast.makeText(context, "✅ MP3 saved successfully!", Toast.LENGTH_LONG).show()
                    statusText.text = "Status: ✅ Success! Saved as $fileName.mp3"
                    Log.i("FFmpeg", "Conversion successful. File: ${tempFile.absolutePath}")
                } else {
                    Toast.makeText(context, "❌ Conversion failed. RC=$rc", Toast.LENGTH_LONG).show()
                    statusText.text = "Status: ❌ Failed (RC=$rc). Check logs."
                    Log.e("FFmpeg", "Conversion failed with RC=$rc")
                    Log.e("FFmpeg", "Error output: $lastCommandOutput")
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            statusText.text = "Status: ❌ Exception occurred"
            Log.e("FFmpeg", "Exception during conversion", e)
            tempFile?.delete()
            outputUri?.let { resolver.delete(it, null, null) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines when fragment is destroyed
        mainScope.coroutineContext[Job]?.cancel()
    }
}