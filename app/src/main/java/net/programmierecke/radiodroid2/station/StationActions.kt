package net.programmierecke.radiodroid2.station

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import net.programmierecke.radiodroid2.ActivityMain
import net.programmierecke.radiodroid2.FavouriteManager
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.alarm.TimePickerFragment
import net.programmierecke.radiodroid2.players.selector.PlayerType
import net.programmierecke.radiodroid2.views.ItemListDialog
import java.lang.ref.WeakReference

object StationActions {
    private const val TAG = "StationActions"

    @JvmStatic
    fun setAsAlarm(activity: FragmentActivity, station: DataRadioStation) {
        val radioDroidApp = activity.applicationContext as RadioDroidApp
        val fragment = TimePickerFragment()
        fragment.setCallback { _, hourOfDay, minute ->
            Log.i(TAG, "Alarm time picked $hourOfDay:$minute")
            radioDroidApp.alarmManager.add(station, hourOfDay, minute)
        }
        fragment.show(activity.supportFragmentManager, "timePicker")
    }

    @JvmStatic
    fun showWebLinks(activity: FragmentActivity, station: DataRadioStation) {
        ItemListDialog.create(activity, intArrayOf(
            R.string.action_station_visit_website,
            R.string.action_station_copy_stream_url,
            R.string.action_station_share
        )) { resourceId ->
            when (resourceId) {
                R.string.action_station_visit_website -> openStationHomeUrl(activity, station)
                R.string.action_station_copy_stream_url -> retrieveAndCopyStreamUrlToClipboard(activity, station)
                R.string.action_station_share -> share(activity, station)
            }
        }.show()
    }

    @JvmStatic
    fun openStationHomeUrl(activity: FragmentActivity, station: DataRadioStation) {
        if (!TextUtils.isEmpty(station.HomePageUrl)) {
            val uri = Uri.parse(station.HomePageUrl)
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    @Suppress("DEPRECATION")
    private fun retrieveAndCopyStreamUrlToClipboard(context: Context, station: DataRadioStation) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)
        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                val ctx = contextRef.get() ?: return null
                val app = ctx.applicationContext as RadioDroidApp
                return Utils.getRealStationLink(app.httpClient, app, station.StationUuid)
            }
            override fun onPostExecute(result: String?) {
                val ctx = contextRef.get() ?: return
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                if (result != null) {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Stream Url", result))
                        Toast.makeText(ctx.applicationContext, ctx.resources.getText(R.string.notify_stream_url_copied), Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "Clipboard is NULL!")
                    }
                } else {
                    Toast.makeText(ctx.applicationContext, ctx.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                }
            }
        }.execute()
    }

    @JvmStatic
    fun markAsFavourite(context: Context, station: DataRadioStation) {
        val app = context.applicationContext as RadioDroidApp
        app.favouriteManager.add(station)
        Toast.makeText(context, context.getString(R.string.notify_starred), Toast.LENGTH_SHORT).show()
        vote(context, station)
    }

    @JvmStatic
    fun removeFromFavourites(context: Context, view: View?, station: DataRadioStation) {
        val app = context.applicationContext as RadioDroidApp
        val favouriteManager: FavouriteManager = app.favouriteManager
        val removedIdx = favouriteManager.remove(station.StationUuid)
        if (view != null) {
            val viewAttachTo = view.rootView.findViewById<View>(R.id.fragment_player_small)
            val snackbar = Snackbar.make(viewAttachTo, R.string.notify_station_removed_from_list, 6000)
            snackbar.anchorView = viewAttachTo
            snackbar.setAction(R.string.action_station_removed_from_list_undo) { favouriteManager.restore(station, removedIdx) }
            snackbar.show()
        }
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun share(context: Context, station: DataRadioStation) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)
        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                val ctx = contextRef.get() ?: return null
                val app = ctx.applicationContext as RadioDroidApp
                return Utils.getRealStationLink(app.httpClient, app, station.StationUuid)
            }
            override fun onPostExecute(result: String?) {
                val ctx = contextRef.get() ?: return
                LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(ActivityMain.ACTION_HIDE_LOADING))
                if (result != null) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, station.Name)
                        putExtra(Intent.EXTRA_TEXT, result)
                    }
                    ctx.startActivity(Intent.createChooser(share, ctx.resources.getString(R.string.share_action)))
                } else {
                    Toast.makeText(ctx.applicationContext, ctx.resources.getText(R.string.error_station_load), Toast.LENGTH_SHORT).show()
                }
            }
        }.execute()
    }

    @JvmStatic
    fun playInRadioDroid(context: Context, station: DataRadioStation) {
        val app = context.applicationContext as RadioDroidApp
        Utils.playAndWarnIfMetered(app, station, PlayerType.RADIODROID) { Utils.play(app, station) }
    }

    @Suppress("DEPRECATION")
    private fun vote(context: Context, station: DataRadioStation) {
        val contextRef = WeakReference(context)
        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg params: Void?): String? {
                val ctx = contextRef.get() ?: return null
                val app = ctx.applicationContext as RadioDroidApp
                return Utils.downloadFeedRelative(app.httpClient, ctx, "json/vote/${station.StationUuid}", true, null)
            }
            override fun onPostExecute(result: String?) {}
        }.execute()
    }
}
