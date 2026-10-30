package com.github.fschroffner.radiodroid3.station

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.FavouriteManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.di.AppEntryPoint
import com.github.fschroffner.radiodroid3.players.selector.PlayerType
import com.github.fschroffner.radiodroid3.views.ItemListDialog
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

object StationActions {
    private const val TAG = "StationActions"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun entryPoint(context: Context): AppEntryPoint =
        EntryPoints.get(context.applicationContext, AppEntryPoint::class.java)

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

    private fun retrieveAndCopyStreamUrlToClipboard(context: Context, station: DataRadioStation) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext null
                val httpClient = entryPoint(ctx).okHttpClient()
                Utils.getRealStationLink(httpClient, ctx, station.StationUuid)
            }
            val ctx = contextRef.get() ?: return@launch
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
    }

    @JvmStatic
    fun markAsFavourite(context: Context, station: DataRadioStation) {
        entryPoint(context).favouriteManager().add(station)
        Toast.makeText(context, context.getString(R.string.notify_starred), Toast.LENGTH_SHORT).show()
        vote(context, station)
    }

    @JvmStatic
    fun removeFromFavourites(context: Context, view: View?, station: DataRadioStation) {
        val favouriteManager: FavouriteManager = entryPoint(context).favouriteManager()
        val removedIdx = favouriteManager.remove(station.StationUuid)
        if (view != null) {
            val anchorView = view.rootView.findViewById<View>(R.id.bottom_sheet)
                ?: view.rootView.findViewById<View>(R.id.fragment_player_small)
            val snackbar = Snackbar.make(view, R.string.notify_station_removed_from_list, 6000)
            if (anchorView != null) {
                snackbar.anchorView = anchorView
            }
            snackbar.setAction(R.string.action_station_removed_from_list_undo) { favouriteManager.restore(station, removedIdx) }
            snackbar.show()
        }
    }

    @JvmStatic
    fun share(context: Context, station: DataRadioStation) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ActivityMain.ACTION_SHOW_LOADING))
        val contextRef = WeakReference(context)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext null
                val httpClient = entryPoint(ctx).okHttpClient()
                Utils.getRealStationLink(httpClient, ctx, station.StationUuid)
            }
            val ctx = contextRef.get() ?: return@launch
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
    }

    @JvmStatic
    fun playInRadioDroid(context: Context, station: DataRadioStation) {
        Utils.playAndWarnIfMetered(context, station, PlayerType.RADIODROID) { Utils.play(station) }
    }

    private fun vote(context: Context, station: DataRadioStation) {
        val contextRef = WeakReference(context)
        scope.launch {
            withContext(Dispatchers.IO) {
                val ctx = contextRef.get() ?: return@withContext
                val httpClient = entryPoint(ctx).okHttpClient()
                Utils.downloadFeedRelative(httpClient, ctx, "json/vote/${station.StationUuid}", true, null)
            }
        }
    }
}
