package com.github.fschroffner.radiodroid3.proxy

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils.parseIntWithDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

class ProxySettingsDialog : DialogFragment() {
    private lateinit var editProxyHost: EditText
    private lateinit var editProxyPort: EditText
    private lateinit var spinnerProxyType: AppCompatSpinner
    private lateinit var editLogin: EditText
    private lateinit var editProxyPassword: EditText
    private lateinit var textProxyTestResult: TextView
    private lateinit var proxyTypeAdapter: ArrayAdapter<Proxy.Type>

    private var proxyTestJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val layout = inflater.inflate(R.layout.dialog_proxy_settings, null)

        editProxyHost = layout.findViewById(R.id.edit_proxy_host)
        editProxyPort = layout.findViewById(R.id.edit_proxy_port)
        spinnerProxyType = layout.findViewById(R.id.spinner_proxy_type)
        editLogin = layout.findViewById(R.id.edit_proxy_login)
        editProxyPassword = layout.findViewById(R.id.edit_proxy_password)
        textProxyTestResult = layout.findViewById(R.id.text_proxy_test_result)

        val context = requireContext().applicationContext
        val settings = ProxySettings().apply { readFromPreferences(PreferenceManager.getDefaultSharedPreferences(context)) }

        editProxyHost.setText(settings.host)
        editProxyPort.setText(settings.port.toString())
        editLogin.setText(settings.login)
        editProxyPassword.setText(settings.password)

        proxyTypeAdapter = ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, arrayOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS)).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerProxyType.adapter = proxyTypeAdapter
        spinnerProxyType.setSelection(proxyTypeAdapter.getPosition(settings.type))

        builder.setView(layout)
            .setPositiveButton(R.string.action_save) { _, _ ->
                createProxySettings().saveToPreferences(PreferenceManager.getDefaultSharedPreferences(context))
            }
            .setNeutralButton(R.string.action_test, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = (it as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL)
            button.setOnClickListener {
                testProxy(createProxySettings())
            }
        }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        proxyTestJob?.cancel()
    }

    private fun createProxySettings() = ProxySettings().apply {
        host = editProxyHost.text.toString()
        port = parseIntWithDefault(editProxyPort.text.toString(), 0)
        login = editLogin.text.toString()
        password = editProxyPassword.text.toString()
        type = proxyTypeAdapter.getItem(spinnerProxyType.selectedItemPosition)
    }

    private fun testProxy(proxySettings: ProxySettings) {
        proxyTestJob?.cancel()
        textProxyTestResult.text = ""

        val context = requireContext().applicationContext
        val successStr = context.getString(R.string.settings_proxy_working, TEST_ADDRESS)
        val failedStr = context.getString(R.string.settings_proxy_not_working)
        val invalidStr = context.getString(R.string.settings_proxy_invalid)

        proxyTestJob = lifecycleScope.launch {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)

            val okHttpClient = if (com.github.fschroffner.radiodroid3.Utils.setOkHttpProxy(builder, proxySettings)) {
                com.github.fschroffner.radiodroid3.Utils.enableTls12OnPreLollipop(builder).build()
            } else {
                null
            }

            if (okHttpClient == null) {
                textProxyTestResult.text = invalidStr
                return@launch
            }

            var requestSucceeded = false
            var errorStr: String? = null

            withContext(Dispatchers.IO) {
                try {
                    val call = okHttpClient.newCall(Request.Builder().url(TEST_ADDRESS).build())
                    val response: Response = call.execute()
                    requestSucceeded = response.isSuccessful
                    if (!requestSucceeded) errorStr = response.message
                } catch (e: IOException) {
                    requestSucceeded = false
                    errorStr = e.message
                }
            }

            textProxyTestResult.text = if (requestSucceeded) {
                successStr
            } else {
                String.format(failedStr, TEST_ADDRESS, errorStr)
            }
        }
    }

    companion object {
        private const val TEST_ADDRESS = "http://radio-browser.info"
    }
}
