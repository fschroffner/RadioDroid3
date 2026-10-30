package com.github.fschroffner.radiodroid3.proxy

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.Utils.parseIntWithDefault
import com.github.fschroffner.radiodroid3.databinding.DialogProxySettingsBinding
import com.google.gson.Gson
import dagger.hilt.EntryPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

class ProxySettingsDialog : DialogFragment() {
    private var _binding: DialogProxySettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var proxyTypeAdapter: ArrayAdapter<Proxy.Type>
    private var proxyTestJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        _binding = DialogProxySettingsBinding.inflate(requireActivity().layoutInflater)

        val context = requireContext().applicationContext
        val entryPoint = EntryPoints.get(context, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java)

        proxyTypeAdapter = ArrayAdapter(
            requireActivity(),
            android.R.layout.simple_spinner_item,
            arrayOf(Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerProxyType.adapter = proxyTypeAdapter

        val initialProxy = entryPoint.appPreferencesDataStore().getProxySettings()
            ?: ProxySettings.fromPreferences(PreferenceManager.getDefaultSharedPreferences(requireContext()))

        initialProxy?.let { ps ->
            binding.editProxyHost.setText(ps.host)
            binding.editProxyPort.setText(ps.port.toString())
            binding.editProxyLogin.setText(ps.login)
            binding.editProxyPassword.setText(ps.password)
            binding.spinnerProxyType.setSelection(proxyTypeAdapter.getPosition(ps.type))
        }

        val dialog = builder.setView(binding.root)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                val proxy = createProxySettings()
                entryPoint.appPreferencesDataStore().putStringAsync("proxySettings", Gson().toJson(proxy))
                val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                proxy.toPreferences(prefs.edit().also { it.apply() })
            }
            .setNeutralButton(R.string.settings_proxy_action_test, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun createProxySettings() = ProxySettings().apply {
        val b = _binding ?: return@apply
        host = b.editProxyHost.text.toString()
        port = parseIntWithDefault(b.editProxyPort.text.toString(), 0)
        login = b.editProxyLogin.text.toString()
        password = b.editProxyPassword.text.toString()
        type = proxyTypeAdapter.getItem(b.spinnerProxyType.selectedItemPosition)
    }

    private fun testProxy(proxySettings: ProxySettings) {
        proxyTestJob?.cancel()
        _binding?.textTestProxyResult?.text = ""

        val context = requireContext().applicationContext
        val successStr = context.getString(R.string.settings_proxy_working, TEST_ADDRESS)
        val failedStr = context.getString(R.string.settings_proxy_not_working)
        val invalidStr = context.getString(R.string.settings_proxy_invalid)

        proxyTestJob = lifecycleScope.launch {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)

            val okHttpClient = if (Utils.setOkHttpProxy(builder, proxySettings)) {
                Utils.enableTls12OnPreLollipop(builder).build()
            } else {
                null
            }

            if (okHttpClient == null) {
                _binding?.textTestProxyResult?.text = invalidStr
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

            _binding?.textTestProxyResult?.text = if (requestSucceeded) {
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