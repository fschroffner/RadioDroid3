package net.programmierecke.radiodroid2.alarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import java.util.Observer

class FragmentAlarm : Fragment(), TimePickerDialog.OnTimeSetListener {
    private lateinit var ram: RadioAlarmManager
    private lateinit var adapterRadioAlarm: ItemAdapterRadioAlarm
    private lateinit var lvAlarms: ListView
    private lateinit var alarmsObserver: Observer
    private var clickedAlarm: DataRadioStationAlarm? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val radioDroidApp = requireActivity().application as RadioDroidApp
        ram = radioDroidApp.getAlarmManager()

        val view = inflater.inflate(R.layout.layout_alarms, container, false)

        adapterRadioAlarm = ItemAdapterRadioAlarm(requireActivity())
        lvAlarms = view.findViewById(R.id.listViewAlarms)
        lvAlarms.adapter = adapterRadioAlarm
        lvAlarms.isClickable = true
        lvAlarms.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val obj = parent.getItemAtPosition(position)
            if (obj is DataRadioStationAlarm) clickOnItem(obj)
        }

        alarmsObserver = Observer { _, _ -> refreshListAndView() }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshListAndView()
        ram.savedAlarmsObservable.addObserver(alarmsObserver)
    }

    override fun onPause() {
        super.onPause()
        ram.savedAlarmsObservable.deleteObserver(alarmsObserver)
    }

    private fun refreshListAndView() {
        adapterRadioAlarm.clear()
        adapterRadioAlarm.addAll(*ram.getList())
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

    fun getRam(): RadioAlarmManager = ram
}
