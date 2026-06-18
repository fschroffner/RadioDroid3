package net.programmierecke.radiodroid2.alarm

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.widget.SwitchCompat
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import java.util.Locale

class ItemAdapterRadioAlarm(private val context: Context) : ArrayAdapter<DataRadioStationAlarm>(context, R.layout.list_item_alarm) {

    private lateinit var ram: RadioAlarmManager

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val radioDroidApp = context.applicationContext as RadioDroidApp
        ram = radioDroidApp.getAlarmManager()

        val aData = getItem(position)!!
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val v = convertView ?: inflater.inflate(R.layout.list_item_alarm, null)

        val tvStation = v.findViewById<TextView>(R.id.textViewStation)
        val tvTime = v.findViewById<TextView>(R.id.textViewTime)
        val s = v.findViewById<SwitchCompat>(R.id.switch1)
        val b = v.findViewById<View>(R.id.buttonDeleteAlarm)
        val buttonRepeating = v.findViewById<View>(R.id.checkboxRepeating)
        val repeatDaysView = v.findViewById<LinearLayout>(R.id.repeatDaysView)

        if (repeatDaysView.childCount < 1) {
            populateWeekDayButtons(aData, inflater, repeatDaysView)
        }

        buttonRepeating.setOnClickListener { ram.toggleRepeating(aData.id) }
        b?.setOnClickListener { ram.remove(aData.id) }

        tvStation?.text = aData.station?.Name
        tvTime?.text = String.format(Locale.getDefault(), "%02d:%02d", aData.hour, aData.minute)

        s?.apply {
            isChecked = aData.enabled
            setOnCheckedChangeListener { _, isChecked ->
                if (BuildConfig.DEBUG) Log.d("ALARM", "new state:$isChecked")
                ram.setEnabled(aData.id, isChecked)
            }
        }

        repeatDaysView.visibility = if (aData.repeating) View.VISIBLE else View.GONE
        buttonRepeating.contentDescription = context.resources.getString(
            if (aData.repeating) R.string.image_button_dont_repeat else R.string.image_button_repeat
        )

        return v
    }

    private fun populateWeekDayButtons(aData: DataRadioStationAlarm, inflater: LayoutInflater, repeatDays: LinearLayout) {
        val weekDayStrings = context.resources.getStringArray(R.array.weekdays)
        for (i in 0 until 7) {
            val viewGroup = inflater.inflate(R.layout.day_button, repeatDays, false) as ViewGroup
            val button = viewGroup.getChildAt(0) as ToggleButton
            repeatDays.addView(viewGroup)
            button.id = i
            button.text = weekDayStrings[i]
            button.textOn = weekDayStrings[i]
            button.textOff = weekDayStrings[i]
            button.contentDescription = weekDayStrings[i]
            if (aData.weekDays.contains(i)) button.isChecked = true
            button.setOnClickListener { ram.changeWeekDays(aData.id, button.id) }
        }
    }
}
