package com.example.musicplayer2.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.musicplayer2.OpenMusicApp
import com.example.musicplayer2.R
import com.example.musicplayer2.data.SongsData
import com.example.musicplayer2.data.SongsData.Companion.getInstance
import com.example.musicplayer2.ui.dir_browser.DirBrowserActivity
import com.example.musicplayer2.ui.sleeptime.SleepTimeActivity

class SettingsFragment : PreferenceFragmentCompat() {
    private var songsData: SongsData? = null
    private var hostCallBack: Host? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        songsData = getInstance(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val libPathPreference = findPreference<Preference>(OpenMusicApp.PREFS_KEY_LIBRARY_PATHS)
        val versions = findPreference<Preference>(OpenMusicApp.PREFS_KEY_VERSION)
        //Preference logging = findPreference(OpenMusicApp.PREFS_KEY_LOGGING);
        val sleeptime = findPreference<Preference>(OpenMusicApp.PREFS_KEY_SLEEPTIME)
        val menuswitch = findPreference<Preference>(OpenMusicApp.PREFS_KEY_MENUSWITCH)
        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult -> if (result.resultCode == Activity.RESULT_OK) hostCallBack!!.onLibraryDirsChanged() }

        assert(libPathPreference != null)
        libPathPreference!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener setOnPreferenceClickListener@{
                if (!songsData!!.isDoneLoading) {
                    Toast.makeText(
                        requireContext(),
                        R.string.settings_cannot_change_lib,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceClickListener false
                }
                val intent = Intent(context, DirBrowserActivity::class.java)
                launcher.launch(intent)
                true
            }

        assert(sleeptime != null)
        sleeptime!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(context, SleepTimeActivity::class.java)
                launcher.launch(intent)
                true
            }

        assert(menuswitch != null)
        menuswitch!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                requireActivity().recreate()
                true
            }

        assert(versions != null)
        // Replace BuildConfig with PackageManager
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versions!!.summary = getString(R.string.about_version, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            versions!!.summary = getString(R.string.about_version, "Unknown")
        }
    }

    /**
     * If the fragment is being attached to another activity
     *
     * @param context The context of the app
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            hostCallBack = context as Host
            // If implementation is missing
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement SettingsFragment.Host")
        }
    }

    interface Host {
        fun onLibraryDirsChanged()
    }
}