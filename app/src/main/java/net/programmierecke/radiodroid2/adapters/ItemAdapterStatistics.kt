package net.programmierecke.radiodroid2.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.data.DataStatistics

class ItemAdapterStatistics(context: Context, private val resourceId: Int) :
    ArrayAdapter<DataStatistics>(context, resourceId) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val aData = getItem(position)
        val v = convertView ?: (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(resourceId, null)

        v.findViewById<TextView>(R.id.stats_name)?.text = aData?.Name
        v.findViewById<TextView>(R.id.stats_value)?.text = aData?.Value

        return v
    }
}
