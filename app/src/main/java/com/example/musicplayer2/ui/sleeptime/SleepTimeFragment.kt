package com.example.musicplayer2.ui.sleeptime

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.musicplayer2.OpenMusicApp
import com.example.musicplayer2.R
import timber.log.Timber
import java.util.*

/**
 * Class that controls the sleeptime functionality
 * This functionality basically allows users to set a time at which the app will stop the music
 * and shutdown. It has a clock widget to set the time and a switch to activate or deactivate it.
 * The settings get written in a Preference which is invisible to the user. Every time the user
 * presses the back key the settings get saved automatically.
 */
class SleepTimeFragment : Fragment() {
    private var preferences: SharedPreferences? = null
    private var editor: SharedPreferences.Editor? = null
    private var timePicker: TimePicker? = null
    private var switchCompat: SwitchCompat? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_sleeptime, container, false)
        preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        timePicker = view.findViewById(R.id.simpleTimePicker)
        timePicker?.setIs24HourView(true) // Changed to 24-hour for easier calculations
        switchCompat = view.findViewById(R.id.timePickerSwitch)
        updateTimePicker()
        switchCompat?.isChecked = preferences!!.getBoolean(
            OpenMusicApp.PREFS_KEY_TIMEPICKER_SWITCH,
            false
        )

        // Detects whenever a user changes the time on the clock or switches from AM to PM
        timePicker?.setOnTimeChangedListener { timePicker: TimePicker, hours: Int, minutes: Int ->
            Timber.d("Time changed: $hours:$minutes")
            timePicker.hour = hours
            timePicker.minute = minutes
        }
        switchCompat?.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            switchCompat?.isChecked = b
            Timber.d("Switch toggled: $b")
        }
        return view
    }

    // Gets if the time is PM or AM
    val amPm: String
        get() = if (timePicker!!.hour < 12) "AM" else "PM"

    // Gets the time in INT, which is also the format it gets saved in the preferences
    private fun convertInt(): Int {
        return timePicker!!.hour * 3600 + timePicker!!.minute * 60
    }

    private fun updateTimePicker() {
        // On time change it automatically updates the variables to match the new time
        val timee = preferences!!.getInt(OpenMusicApp.PREFS_KEY_TIMEPICKER, 36480) // Default 10:08 AM
        val min = timee / 60 % 60
        val hours = timee / 60 / 60
        timePicker!!.hour = hours
        timePicker!!.minute = min
        Timber.d("Updated time picker: $hours:$min")
    }

    // True if the switch is on, else false
    private fun switchState(): Boolean {
        return switchCompat!!.isChecked
    }

    // Modified onBackPressed() function in SleepTimeFragment.kt

    fun onBackPressed() {
        editor = preferences!!.edit()
        editor?.putInt(OpenMusicApp.PREFS_KEY_TIMEPICKER, convertInt())
        editor?.putBoolean(OpenMusicApp.PREFS_KEY_TIMEPICKER_SWITCH, switchState())
        editor?.apply()

        // Set result for parent activity
        val activity = requireActivity()

        if (switchState()) {
            // FIXED: Calculate time until the selected time
            val delayMillis = calculateDelayUntilTime()
            if (delayMillis > 0) {
                scheduleSleepTimer(delayMillis)
                // Set result indicating timer was activated
                activity.setResult(android.app.Activity.RESULT_OK, android.content.Intent().apply {
                    putExtra("sleep_timer_active", true)
                    putExtra("timer_hours", timePicker!!.hour)
                    putExtra("timer_minutes", timePicker!!.minute)
                })
            } else {
                Toast.makeText(
                    requireContext(),
                    "Selected time has already passed today. Please choose a future time.",
                    Toast.LENGTH_LONG
                ).show()
                // Set result indicating timer was not set
                activity.setResult(android.app.Activity.RESULT_OK, android.content.Intent().apply {
                    putExtra("sleep_timer_active", false)
                })
            }
        } else {
            cancelSleepTimer()
            // Set result indicating timer was deactivated
            activity.setResult(android.app.Activity.RESULT_OK, android.content.Intent().apply {
                putExtra("sleep_timer_active", false)
            })
        }

        activity.finish()
    }

    /**
     * Calculate milliseconds from now until the selected time
     * If the time has passed today, schedule for tomorrow
     */
    private fun calculateDelayUntilTime(): Long {
        val calendar = Calendar.getInstance()
        val currentTimeMillis = calendar.timeInMillis

        // Set calendar to the selected time today
        calendar.set(Calendar.HOUR_OF_DAY, timePicker!!.hour)
        calendar.set(Calendar.MINUTE, timePicker!!.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        var targetTimeMillis = calendar.timeInMillis

        // If the time has already passed today, schedule for tomorrow
        if (targetTimeMillis <= currentTimeMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            targetTimeMillis = calendar.timeInMillis
        }

        val delayMillis = targetTimeMillis - currentTimeMillis
        Timber.d("Sleep timer delay: $delayMillis ms (${delayMillis/1000/60} minutes)")

        return delayMillis
    }

    private fun scheduleSleepTimer(delayMillis: Long) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if we can schedule exact alarms on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Timber.w("Cannot schedule exact alarms - requesting permission")
                requestExactAlarmPermission()
                return
            }
        }

        val intent = Intent(requireContext(), SleepTimeReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            requireContext(),
            SLEEP_TIMER_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val triggerAt = System.currentTimeMillis() + delayMillis

            // Use setExactAndAllowWhileIdle for better reliability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }

            val hours = timePicker!!.hour
            val minutes = timePicker!!.minute
            val timeString = String.format("%02d:%02d", hours, minutes)

            Toast.makeText(
                requireContext(),
                "Sleep timer set for $timeString",
                Toast.LENGTH_LONG
            ).show()

            Timber.i("Sleep timer scheduled for $triggerAt (in ${delayMillis/1000} seconds)")

        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException scheduling alarm")
            Toast.makeText(
                requireContext(),
                "Unable to set sleep timer. Please check app permissions.",
                Toast.LENGTH_LONG
            ).show()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestExactAlarmPermission()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scheduling sleep timer")
            Toast.makeText(
                requireContext(),
                "Error setting sleep timer: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)

                Toast.makeText(
                    requireContext(),
                    "Please enable 'Alarms & reminders' permission for sleep timer to work",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Timber.e(e, "Error requesting exact alarm permission")
                Toast.makeText(
                    requireContext(),
                    "Please enable 'Alarms & reminders' permission in app settings for sleep timer to work",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun cancelSleepTimer() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), SleepTimeReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            requireContext(),
            SLEEP_TIMER_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        Toast.makeText(
            requireContext(),
            "Sleep timer cancelled",
            Toast.LENGTH_SHORT
        ).show()

        Timber.i("Sleep timer cancelled")
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Permission not required on older versions
        }
    }

    companion object {
        private const val SLEEP_TIMER_REQUEST_CODE = 1001
    }
}