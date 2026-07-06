package com.github.fschroffner.radiodroid3.alarm

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import com.github.fschroffner.radiodroid3.Utils
import java.util.Calendar

class TimePickerFragment : DialogFragment, TimePickerDialog.OnTimeSetListener {
    private var callback: TimePickerDialog.OnTimeSetListener? = null
    private val initialHour: Int
    private val initialMinute: Int

    constructor() {
        val c = Calendar.getInstance()
        initialHour = c.get(Calendar.HOUR_OF_DAY)
        initialMinute = c.get(Calendar.MINUTE)
    }

    constructor(initialHour: Int, initialMinute: Int) {
        this.initialHour = initialHour
        this.initialMinute = initialMinute
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        TimePickerDialog(requireActivity(), Utils.getTimePickerThemeResId(requireActivity()), this, initialHour, initialMinute, DateFormat.is24HourFormat(requireActivity()))

    fun setCallback(callback: TimePickerDialog.OnTimeSetListener) { this.callback = callback }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        // Guard against double callback on some devices
        callback?.let { it.onTimeSet(view, hourOfDay, minute); callback = null }
    }
}
