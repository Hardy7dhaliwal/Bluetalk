package com.example.bluetalk.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.example.bluetalk.R
import com.example.bluetalk.bluetooth.BluetalkServer

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        updateUsernameSummary()
    }
    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "username") {
            updateUsernameSummary()
        }
    }

    private fun updateUsernameSummary() {
        val usernamePreference: EditTextPreference? = findPreference("username")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val username = sharedPreferences.getString("username", "Not set")
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetalkServer.disconnectAll()
    }
}