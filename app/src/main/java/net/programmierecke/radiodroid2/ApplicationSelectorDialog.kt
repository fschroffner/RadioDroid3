package net.programmierecke.radiodroid2

import android.app.Dialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import net.programmierecke.radiodroid2.interfaces.IApplicationSelected

class ApplicationSelectorDialog : DialogFragment() {
    private val listInfos = mutableListOf<ActivityInfo>()
    private var callback: IApplicationSelected? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arrayAdapter = ArrayAdapter<String>(requireActivity(), android.R.layout.select_dialog_singlechoice)

        val pm = requireContext().packageManager
        val mainIntent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("http://example.com/test.mp3"), "audio/*") }
        val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resolveInfos) {
            val appInfo = info.activityInfo.applicationInfo
            if (BuildConfig.DEBUG) Log.d("UUU", "${appInfo.packageName} -- ${info.activityInfo.name}")
            arrayAdapter.add(pm.getApplicationLabel(appInfo).toString())
            listInfos.add(info.activityInfo)
        }

        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.alert_select_external_alarm_app)
            .setAdapter(arrayAdapter) { _, which ->
                if (BuildConfig.DEBUG) Log.d("AAA", "choose : $which")
                val info = listInfos[which]
                callback?.onAppSelected(info.packageName, info.name)
            }
            .create()
    }

    fun setCallback(callback: IApplicationSelected) { this.callback = callback }
}
