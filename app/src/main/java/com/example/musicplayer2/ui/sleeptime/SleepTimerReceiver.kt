package com.example.musicplayer2.ui.sleeptime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.example.musicplayer2.MediaPlayerService
import com.example.musicplayer2.MediaPlayerUtil
import com.example.musicplayer2.OpenMusicApp
import timber.log.Timber

class SleepTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("Sleep timer expired! Attempting to stop MediaPlayerService")

        // Update SharedPreferences to mark sleep timer as inactive
        updateSleepTimerPreferences(context)

        // Show user notification
        try {
            Toast.makeText(context, "Sleep timer activated - stopping music", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.w(e, "Could not show toast (app might be in background)")
        }

        // Log the intent details for debugging
        Timber.d("Received intent: action=${intent.action}, extras=${intent.extras}")

        var success = false

        // Method 1: Try to fade out via MediaPlayerUtil directly
        try {
            Timber.i("Attempting fade out via MediaPlayerUtil")
            MediaPlayerUtil.fadeOutAndStop(3000L) { // 3-second fade
                Timber.i("Fade out completed, now closing app")
                closeApp(context)
            }
            success = true
            Timber.i("Successfully started fade out via MediaPlayerUtil")
        } catch (e: Exception) {
            Timber.w(e, "Direct MediaPlayerUtil fade failed, trying service approach")
        }

        // Method 2: Try stopping via service with fade if direct method failed
        if (!success) {
            val stopIntent = Intent(context, MediaPlayerService::class.java).apply {
                action = MediaPlayerService.ACTION_FADE_OUT_STOP // New action for fade out
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(stopIntent)
                    Timber.i("Successfully sent ACTION_FADE_OUT_STOP via startForegroundService")
                } else {
                    context.startService(stopIntent)
                    Timber.i("Successfully sent ACTION_FADE_OUT_STOP via startService")
                }

                // Close app after a delay to allow fade to complete
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    closeApp(context)
                }, 4000L) // Wait 4 seconds for 3-second fade + buffer

                success = true
            } catch (e: IllegalStateException) {
                Timber.e(e, "IllegalStateException when stopping service - app may be in background")
            } catch (e: SecurityException) {
                Timber.e(e, "SecurityException when stopping service")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error stopping service")
            }
        }

        // Method 3: Try broadcast as fallback
        if (!success) {
            Timber.w("Service method failed, trying broadcast fallback")
            try {
                val broadcastIntent = Intent().apply {
                    action = MediaPlayerService.ACTION_STOP
                    setPackage(context.packageName)
                }
                context.sendBroadcast(broadcastIntent)
                Timber.i("Sent stop music broadcast as fallback")

                // Close app after short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    closeApp(context)
                }, 1000L)

                success = true
            } catch (e: Exception) {
                Timber.e(e, "Broadcast fallback also failed")
            }
        }

        // Method 4: Last resort - immediate stop and close
        if (!success) {
            Timber.w("All methods failed, immediate stop and close")
            try {
                MediaPlayerUtil.stop()
                closeApp(context)
                success = true
            } catch (e: Exception) {
                Timber.e(e, "Even immediate stop failed")
            }
        }

        // Log final result
        if (success) {
            Timber.i("Sleep timer: Successfully initiated music fade out and app closure")
        } else {
            Timber.e("Sleep timer: All stop attempts failed!")
        }
    }

    /**
     * Gracefully closes the app by finishing all activities
     */
    private fun closeApp(context: Context) {
        try {
            Timber.i("Attempting to close app gracefully")

            // Method 1: Send broadcast to close all activities
            val closeIntent = Intent("com.example.musicplayer2.CLOSE_APP").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(closeIntent)

            // Method 2: Try to finish main activity specifically
            val mainActivityIntent = Intent(context, com.example.musicplayer2.ui.main.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("FINISH_APP", true)
            }

            try {
                context.startActivity(mainActivityIntent)
            } catch (e: Exception) {
                Timber.w(e, "Could not start main activity to close app")
            }

            // Method 3: As last resort after a delay, kill process
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Timber.i("Closing app process as final step")
                    android.os.Process.killProcess(android.os.Process.myPid())
                } catch (e: Exception) {
                    Timber.e(e, "Could not kill process")
                }
            }, 2000L) // Wait 2 seconds before force close

        } catch (e: Exception) {
            Timber.e(e, "Error closing app")
        }
    }

    private fun updateSleepTimerPreferences(context: Context) {
        try {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preferences.edit()
            editor.putBoolean(OpenMusicApp.PREFS_KEY_TIMEPICKER_SWITCH, false)
            editor.apply()
            Timber.d("Updated sleep timer preferences to inactive state")
        } catch (e: Exception) {
            Timber.e(e, "Error updating sleep timer preferences")
        }
    }
}