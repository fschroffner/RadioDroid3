package com.github.fschroffner.radiodroid3.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference

/** Hack for integer preferences — only valid integers are persisted. */
class IntEditTextPreference : EditTextPreference {
    private var value = 0
    private var summaryFormat: String? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { summaryFormat = summary?.toString() }
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) { summaryFormat = summary?.toString() }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = if (defaultValue == null) getPersistedInt(0)
                else defaultValue.toString().toIntOrNull() ?: 0
        summaryFormat?.let { setSummary(String.format(it, value)) }
    }

    override fun setText(text: String?) {
        val wasBlocking = shouldDisableDependents()
        text?.toIntOrNull()?.let {
            value = it
            persistInt(it)
            summaryFormat?.let { fmt -> setSummary(String.format(fmt, value)) }
        }
        if (isBlocking != wasBlocking) notifyDependencyChange(shouldDisableDependents())
    }

    private val isBlocking get() = shouldDisableDependents()

    override fun getText(): String = value.toString()

    override fun getPersistedString(defaultReturnValue: String?): String = getPersistedInt(value).toString()
}
