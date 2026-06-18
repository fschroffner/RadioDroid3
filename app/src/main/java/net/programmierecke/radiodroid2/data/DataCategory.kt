package net.programmierecke.radiodroid2.data

import android.graphics.drawable.Drawable
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException

class DataCategory : Comparable<DataCategory> {
    var Name: String = ""
    var UsedCount: Int = 0
    var Label: String? = null
    var Icon: Drawable? = null

    fun getSortField(): String = Label ?: Name

    override fun compareTo(other: DataCategory): Int = getSortField().compareTo(other.getSortField(), ignoreCase = true)

    companion object {
        @JvmStatic
        fun DecodeJson(result: String?): Array<DataCategory> {
            val list = mutableListOf<DataCategory>()
            if (result != null && TextUtils.isGraphic(result)) {
                try {
                    val jsonArray = JSONArray(result)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(DataCategory().apply {
                            Name = obj.getString("name")
                            UsedCount = obj.getInt("stationcount")
                        })
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            return list.toTypedArray()
        }
    }
}
