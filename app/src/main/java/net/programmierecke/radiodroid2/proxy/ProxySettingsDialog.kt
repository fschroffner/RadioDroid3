package net.programmierecke.radiodroid2.proxy

import android.app.Dialog
import android.content.DialogInterface
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils.parseIntWithDefault
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.ref.WeakReference
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

    @Suppress("DEPRECATION")
    private var proxyTestTask: AsyncTask<*, *, *>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val layout = inflater.inflate(R.layout.dialog_proxy_settings, null)

        editProxyHost = layout.findViewById(R.id.edit_proxy_host)
        editProxyPort = layout.findViewById(R.id.edit_proxy_port)
        spinnerProxyType = layout.findViewById(R.id.spinner_proxy_type)
        editLogin = layout.findViewById(R.id.edit_proxy_login)
        editProxyPassword = layout.findViewById(R.id.edit_proxy_password)
        textProxyTestResult = layout.findViewById(R.id.text_test_proxy_result)

        proxyTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, arrayOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS))
        spinnerProxyType.adapter = proxyTypeAdapter

        ProxySettings.fromPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))?.let { ps ->
            editProxyHost.setText(ps.host)
            editProxyPort.setText(ps.port.toString())
            editLogin.setText(ps.login)
            editProxyPassword.setText(ps.password)
            spinnerProxyType.setSelection(proxyTypeAdapter.getPosition(ps.type))
        }

        val dialog = builder.setView(layout)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                createProxySettings().toPreferences(prefs.edit().also { it.apply() })
                (requireActivity().application as RadioDroidApp).rebuildHttpClient()
            }
            .setNegativeButton(R.string.action_cancel) { d, _ -> d.cancel() }
            .setNeutralButton(R.string.settings_proxy_action_test, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { testProxy(createProxySettings()) }
        }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        proxyTestTask?.cancel(true)
    }

    private fun createProxySettings() = ProxySettings().apply {
        host = editProxyHost.text.toString()
        port = parseIntWithDefault(editProxyPort.text.toString(), 0)
        login = editLogin.text.toString()
        password = editProxyPassword.text.toString()
        type = proxyTypeAdapter.getItem(spinnerProxyType.selectedItemPosition)
    }

    @Suppress("DEPRECATION")
    private fun testProxy(proxySettings: ProxySettings) {
        proxyTestTask?.cancel(true)
        val app = requireActivity().application as RadioDroidApp
        proxyTestTask = ConnectionTesterTask(app, textProxyTestResult, proxySettings)
        proxyTestTask!!.execute()
    }

    @Suppress("DEPRECATION")
    private class ConnectionTesterTask(
        app: RadioDroidApp,
        textView: TextView,
        proxySettings: ProxySettings
    ) : AsyncTask<Void, Void, Void>() {
        private val textRef = WeakReference(textView)
        private var call: Call? = null
        private var okHttpClient: okhttp3.OkHttpClient? = null
        private val successStr = app.getString(R.string.settings_proxy_working, TEST_ADDRESS)
        private val failedStr = app.getString(R.string.settings_proxy_not_working)
        private val invalidStr = app.getString(R.string.settings_proxy_invalid)
        private var requestSucceeded = false
        private var errorStr: String? = null

        init {
            textView.text = ""
            val builder = app.newHttpClientWithoutProxy()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
            if (net.programmierecke.radiodroid2.Utils.setOkHttpProxy(builder, proxySettings)) {
                okHttpClient = builder.build()
            }
        }

        override fun onPreExecute() {
            okHttpClient ?: return
            call = okHttpClient!!.newCall(Request.Builder().url(TEST_ADDRESS).build())
        }

        override fun doInBackground(vararg params: Void?): Void? {
            okHttpClient ?: return null
            try {
                val response: Response = call!!.execute()
                requestSucceeded = response.isSuccessful
                if (!requestSucceeded) errorStr = response.message
            } catch (e: IOException) { requestSucceeded = false; errorStr = e.message }
            return null
        }

        override fun onPostExecute(v: Void?) {
            val textResult = textRef.get() ?: return
            textResult.text = when {
                okHttpClient == null -> invalidStr
                requestSucceeded -> successStr
                else -> String.format(failedStr, TEST_ADDRESS, errorStr)
            }
        }
    }

    companion object {
        private const val TEST_ADDRESS = "http://radio-browser.info"
    }
}
