package com.github.fschroffner.radiodroid3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class FragmentBase : Fragment() {
    private val TAG = "FragmentBase"

    private var relativeUrl: String? = null
    protected var urlResult: String? = null
    private var isCreated = false
    protected var downloadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCreated = true
        if (relativeUrl == null) relativeUrl = arguments?.getString("url")
        DownloadUrl(false)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        super.onDestroy()
    }

    protected fun hasUrl() = !TextUtils.isEmpty(relativeUrl)

    fun DownloadUrl(forceUpdate: Boolean) = DownloadUrl(forceUpdate, true)

    fun DownloadUrl(forceUpdate: Boolean, displayProgress: Boolean) {
        if (!isCreated) return
        downloadJob?.cancel()
        downloadJob = null

        val showBroken = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("show_broken", false)

        if (BuildConfig.DEBUG) Log.d(TAG, "Download relativeUrl:$relativeUrl")

        if (TextUtils.isGraphic(relativeUrl)) {
            val cache = Utils.getCacheFile(requireActivity(), relativeUrl!!)
            if (cache == null || forceUpdate) {
                if (context != null && displayProgress) {
                    LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
                }

                val httpClient = dagger.hilt.EntryPoints.get(requireContext().applicationContext, com.github.fschroffner.radiodroid3.di.AppEntryPoint::class.java).okHttpClient()

                downloadJob = lifecycleScope.launch {
                    val p = hashMapOf("hidebroken" to "${!showBroken}")
                    val result = withContext(Dispatchers.IO) {
                        Utils.downloadFeedRelative(httpClient, requireActivity(), relativeUrl!!, forceUpdate, p)
                    }
                    DownloadFinished()
                    if (context != null) {
                        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Download relativeUrl finished:$relativeUrl")
                    if (result != null) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Download relativeUrl OK:$relativeUrl")
                        urlResult = result
                        RefreshListGui()
                    } else {
                        try {
                            Toast.makeText(context, resources.getText(R.string.error_list_update), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("ERR", e.toString())
                        }
                    }
                }
            } else {
                urlResult = cache
                DownloadFinished()
                RefreshListGui()
            }
        } else {
            RefreshListGui()
        }
    }

    protected open fun RefreshListGui() {}
    protected open fun DownloadFinished() {}
}
