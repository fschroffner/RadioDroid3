package com.github.fschroffner.radiodroid3

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.github.fschroffner.radiodroid3.interfaces.IApplicationSelected
import com.github.fschroffner.radiodroid3.proxy.ProxySettingsDialog

class FragmentSettings : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    IApplicationSelected,
    PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    companion object {
        @JvmStatic
        fun openNewSettingsSubFragment(activity: ActivityMain, key: String): FragmentSettings {
            val f = FragmentSettings()
            f.arguments = Bundle().apply { putString(ARG_PREFERENCE_ROOT, key) }
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.containerView, f)
                .addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString())
                .commit()
            return f
        }
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat, screen: PreferenceScreen): Boolean {
        openNewSettingsSubFragment(requireActivity() as ActivityMain, screen.key)
        return true
    }

    private fun isToplevel() = preferenceScreen == null || preferenceScreen.key == "pref_toplevel"

    private fun refreshToplevelIcons() {
        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        findPreference<Preference>("pref_category_ui")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon2.cmd_monitor)
        findPreference<Preference>("pref_category_startup")?.icon = Utils.IconicsIcon(requireContext(),GoogleMaterial.Icon.gmd_flight_takeoff)
        findPreference<Preference>("pref_category_interaction")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon.cmd_gesture_tap)
        findPreference<Preference>("pref_category_player")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon2.cmd_play)
        findPreference<Preference>("pref_category_alarm")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon.cmd_clock_outline)
        findPreference<Preference>("pref_category_connectivity")?.icon = Utils.IconicsIcon(requireContext(),GoogleMaterial.Icon.gmd_import_export)
        findPreference<Preference>("pref_category_recordings")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon2.cmd_record_rec)
        findPreference<Preference>("pref_category_mpd")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon2.cmd_speaker_wireless)
        findPreference<Preference>("pref_category_other")?.icon = Utils.IconicsIcon(requireContext(),CommunityMaterial.Icon2.cmd_information_outline)
    }

    private fun refreshToolbar() {
        val activity = activity as? ActivityMain ?: return
        val toolbar = activity.getToolbar() ?: return
        val screen = preferenceScreen ?: return

        toolbar.title = screen.title

        if (Utils.bottomNavigationEnabled(activity)) {
            if (isToplevel()) {
                activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
                activity.supportActionBar?.setDisplayShowHomeEnabled(false)
            } else {
                activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
                activity.supportActionBar?.setDisplayShowHomeEnabled(true)
            }
            toolbar.setNavigationOnClickListener { activity.onBackPressed() }
        }
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        setPreferencesFromResource(R.xml.preferences, s)
        refreshToolbar()

        when (s) {
            null -> {
                refreshToplevelIcons()
                val searchPreference = findPreference<SearchPreference>("searchPreference")!!
                val config: SearchConfiguration = searchPreference.searchConfiguration
                config.setActivity(requireActivity() as AppCompatActivity)
                config.index(R.xml.preferences)
            }
            "pref_category_player" -> {
                findPreference<Preference>("equalizer")?.setOnPreferenceClickListener {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    if (requireContext().packageManager.resolveActivity(intent, 0) == null) {
                        Toast.makeText(context, R.string.error_no_equalizer_found, Toast.LENGTH_SHORT).show()
                    } else {
                        intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        startActivityForResult(intent, ActivityMain.LAUNCH_EQUALIZER_REQUEST)
                    }
                    false
                }
            }
            "pref_category_connectivity" -> {
                findPreference<Preference>("settings_proxy")?.setOnPreferenceClickListener {
                    ProxySettingsDialog().apply { isCancelable = true }
                        .show(parentFragmentManager, "")
                    false
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    findPreference<Preference>("settings_retry_timeout")?.isVisible = false
                    findPreference<Preference>("settings_retry_delay")?.isVisible = false
                }
            }
            "pref_category_mpd" -> {
                findPreference<Preference>("mpd_servers_viewer")?.setOnPreferenceClickListener {
                    val app = requireActivity().application as RadioDroidApp
                    Utils.showMpdServersDialog(app, requireActivity().supportFragmentManager, null)
                    false
                }
            }
            "pref_category_other" -> {
                findPreference<Preference>("show_statistics")?.setOnPreferenceClickListener {
                    (requireActivity() as ActivityMain).getToolbar()?.setTitle(R.string.settings_statistics)
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.containerView, FragmentServerInfo())
                        .addToBackStack(ActivityMain.FRAGMENT_FROM_BACKSTACK.toString())
                        .commit()
                    false
                }
            }
        }

        val batPref = preferenceScreen.findPreference<Preference>(getString(R.string.key_ignore_battery_optimization))
        if (batPref != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                updateBatteryPrefDescription(batPref)
                batPref.setOnPreferenceClickListener {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    updateBatteryPrefDescription(batPref)
                    true
                }
            } else {
                batPref.parent?.removePreference(batPref)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        refreshToolbar()
        if (isToplevel()) refreshToplevelIcons()
        findPreference<Preference>("shareapp_package")?.summary = preferenceManager.sharedPreferences?.getString("shareapp_package", "")
        val batPref = preferenceScreen.findPreference<Preference>(getString(R.string.key_ignore_battery_optimization))
        if (batPref != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateBatteryPrefDescription(batPref)
        }
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    @RequiresApi(23)
    private fun updateBatteryPrefDescription(batPref: Preference) {
        val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        batPref.setSummary(
            if (pm.isIgnoringBatteryOptimizations(requireContext().packageName))
                R.string.settings_ignore_battery_optimization_summary_on
            else
                R.string.settings_ignore_battery_optimization_summary_off
        )
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (BuildConfig.DEBUG) Log.d("AAA", "changed key:$key")
        when (key) {
            "alarm_external" -> {
                if (sharedPreferences.getBoolean(key, false)) {
                    ApplicationSelectorDialog().apply { setCallback(this@FragmentSettings) }
                        .show(requireActivity().supportFragmentManager, "appPicker")
                }
            }
            "theme_name", "circular_icons", "bottom_navigation" -> {
                if (key == "circular_icons") {
                    (requireActivity().application as RadioDroidApp).favouriteManager.updateShortcuts()
                }
                requireActivity().recreate()
            }
        }
    }

    override fun onAppSelected(packageName: String, activityName: String) {
        if (BuildConfig.DEBUG) Log.d("SEL", "selected:$packageName/$activityName")
        preferenceManager.sharedPreferences?.edit()?.apply {
            putString("shareapp_package", packageName)
            putString("shareapp_activity", activityName)
            commit()
        }
        findPreference<Preference>("shareapp_package")?.summary = packageName
    }
}
