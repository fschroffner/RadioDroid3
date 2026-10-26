package com.github.fschroffner.radiodroid3.alarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.fschroffner.radiodroid3.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FragmentAlarm : Fragment(), TimePickerDialog.OnTimeSetListener {
    @Inject
    lateinit var ram: RadioAlarmManager

    private lateinit var adapterRadioAlarm: ItemAdapterRadioAlarm
    private lateinit var lvAlarms: ListView
    private var clickedAlarm: DataRadioStationAlarm? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_alarms, container, false)

        adapterRadioAlarm = ItemAdapterRadioAlarm(requireActivity(), ram)
        lvAlarms = view.findViewById(R.id.listViewAlarms)
        lvAlarms.adapter = adapterRadioAlarm
        lvAlarms.isClickable = true
        lvAlarms.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val obj = parent.getItemAtPosition(position)
            if (obj is DataRadioStationAlarm) clickOnItem(obj)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ram.alarmsFlow.collect { alarms ->
                    adapterRadioAlarm.clear()
                    adapterRadioAlarm.addAll(*alarms.toTypedArray())
                }
            }
        }
    }

    private fun clickOnItem(alarm: DataRadioStationAlarm) {
        clickedAlarm = alarm
        val fragment = TimePickerFragment(alarm.hour, alarm.minute)
        fragment.setCallback(this)
        fragment.show(requireActivity().supportFragmentManager, "timePicker")
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        clickedAlarm?.let { ram.changeTime(it.id, hourOfDay, minute) }
        view.invalidate()
    }
}
