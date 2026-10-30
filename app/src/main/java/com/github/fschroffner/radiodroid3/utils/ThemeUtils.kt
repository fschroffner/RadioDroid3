package com.github.fschroffner.radiodroid3.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.R

object ThemeUtils {

    @JvmStatic
    fun getTheme(context: Context): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val spTheme = sharedPref.getString("theme_name", null)
        if (!spTheme.isNullOrBlank()) {
            return spTheme
        }
        try {
            val entryPoint = dagger.hilt.EntryPoints.get(
                context.applicationContext,
                com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java
            )
            val dsTheme = entryPoint.appPreferencesDataStore().getString("theme_name", null)
            if (!dsTheme.isNullOrBlank()) {
                sharedPref.edit().putString("theme_name", dsTheme).apply()
                return dsTheme
            }
        } catch (_: Exception) {}
        return context.resources.getString(R.string.theme_light)
    }

    private fun isSystemDarkMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    fun getThemeResId(context: Context): Int {
        val theme = getTheme(context)
        val themeValues = try { context.resources.getStringArray(R.array.theme_values) } catch (_: Exception) { emptyArray() }
        val themeEntries = try { context.resources.getStringArray(R.array.theme_entries) } catch (_: Exception) { emptyArray() }

        val darkVal = if (themeValues.size > 1) themeValues[1] else ""
        val darkEntry = if (themeEntries.size > 1) themeEntries[1] else ""
        val oledVal = if (themeValues.size > 2) themeValues[2] else ""
        val oledEntry = if (themeEntries.size > 2) themeEntries[2] else ""
        val sysVal = if (themeValues.size > 3) themeValues[3] else ""
        val sysEntry = if (themeEntries.size > 3) themeEntries[3] else ""

        return when {
            theme.equals("dark", ignoreCase = true) ||
            theme.equals("dunkel", ignoreCase = true) ||
            theme.equals(darkVal, ignoreCase = true) ||
            theme.equals(darkEntry, ignoreCase = true) ||
            theme.equals(context.resources.getString(R.string.theme_dark), ignoreCase = true) -> R.style.MyMaterialTheme_Dark

            theme.equals("oled", ignoreCase = true) ||
            theme.contains("oled", ignoreCase = true) ||
            theme.equals(oledVal, ignoreCase = true) ||
            theme.equals(oledEntry, ignoreCase = true) ||
            theme.equals(context.resources.getString(R.string.theme_oled), ignoreCase = true) -> R.style.MyMaterialTheme_Oled

            theme.equals("system", ignoreCase = true) ||
            theme.startsWith("system", ignoreCase = true) ||
            theme.equals(sysVal, ignoreCase = true) ||
            theme.equals(sysEntry, ignoreCase = true) ||
            theme.equals(context.resources.getString(R.string.theme_system), ignoreCase = true) ->
                if (isSystemDarkMode(context)) R.style.MyMaterialTheme_Dark else R.style.MyMaterialTheme

            else -> R.style.MyMaterialTheme
        }
    }

    @JvmStatic
    fun applyTheme(activity: Activity) {
        val themeResId = getThemeResId(activity)
        activity.setTheme(themeResId)

        val nightMode = when (themeResId) {
            R.style.MyMaterialTheme_Dark, R.style.MyMaterialTheme_Oled -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                val theme = getTheme(activity)
                if (theme.contains("system", ignoreCase = true)) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean {
        val themeId = getThemeResId(context)
        return themeId == R.style.MyMaterialTheme_Dark || themeId == R.style.MyMaterialTheme_Oled
    }

    @JvmStatic
    fun getTimePickerThemeResId(context: Context): Int {
        return when (getThemeResId(context)) {
            R.style.MyMaterialTheme_Dark -> R.style.DialogTheme_Dark
            R.style.MyMaterialTheme_Oled -> R.style.DialogTheme_Oled
            else -> R.style.DialogTheme
        }
    }

    @JvmStatic
    fun themeAttributeToColor(themeAttributeId: Int, context: Context, fallbackColorId: Int): Int {
        val outValue = TypedValue()
        val theme = context.theme
        val wasResolved = theme.resolveAttribute(themeAttributeId, outValue, true)
        return if (wasResolved) {
            if (outValue.resourceId == 0) outValue.data else ContextCompat.getColor(context, outValue.resourceId)
        } else {
            fallbackColorId
        }
    }

    @JvmStatic
    fun getIconColor(context: Context): Int = themeAttributeToColor(R.attr.menuTextColorDefault, context, Color.LTGRAY)

    @JvmStatic
    fun getAccentColor(context: Context): Int = themeAttributeToColor(R.attr.colorAccent, context, Color.LTGRAY)
}
