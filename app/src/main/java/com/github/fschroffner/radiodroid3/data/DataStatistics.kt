package com.github.fschroffner.radiodroid3.data

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

class DataStatistics {
    var Name: String = ""
    var Value: String = ""

    companion object {
        @JvmStatic
        fun DecodeJson(result: String?): Array<DataStatistics> {
            val list = mutableListOf<DataStatistics>()
            if (result != null && TextUtils.isGraphic(result)) {
                try {
                    val jsonObject = JSONObject(result)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        list.add(DataStatistics().apply {
                            Name = key
                            Value = jsonObject.getString(key)
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
