package net.programmierecke.radiodroid2

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager

open class FragmentBase : Fragment() {
    private val TAG = "FragmentBase"

    private var relativeUrl: String? = null
    protected var urlResult: String? = null
    private var isCreated = false
    private var task: AsyncTask<*, *, *>? = null

    override fun onAttach(context: Context) { super.onAttach(context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCreated = true
        if (relativeUrl == null) relativeUrl = arguments?.getString("url")
        DownloadUrl(false)
    }

    override fun onDestroy() {
        task?.cancel(true)
        super.onDestroy()
    }

    protected fun hasUrl() = !TextUtils.isEmpty(relativeUrl)

    fun DownloadUrl(forceUpdate: Boolean) = DownloadUrl(forceUpdate, true)

    @Suppress("DEPRECATION")
    fun DownloadUrl(forceUpdate: Boolean, displayProgress: Boolean) {
        if (!isCreated) return
        task?.cancel(true)
        task = null

        val showBroken = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean("show_broken", false)

        if (BuildConfig.DEBUG) Log.d(TAG, "Download relativeUrl:$relativeUrl")

        if (TextUtils.isGraphic(relativeUrl)) {
            val cache = Utils.getCacheFile(requireActivity(), relativeUrl)
            if (cache == null || forceUpdate) {
                if (context != null && displayProgress) {
                    LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
                }

                val httpClient = (requireActivity().application as RadioDroidApp).httpClient

                task = object : AsyncTask<Void, Void, String?>() {
                    override fun doInBackground(vararg params: Void?): String? {
                        val p = hashMapOf("hidebroken" to "${!showBroken}")
                        return Utils.downloadFeedRelative(httpClient, requireActivity(), relativeUrl, forceUpdate, p)
                    }
                    override fun onPostExecute(result: String?) {
                        DownloadFinished()
                        if (context != null)
                            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
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
                }.execute()
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
