package com.github.fschroffner.radiodroid3.station

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils

object StationShortcutHelper {

    interface ShortcutReadyListener {
        fun onShortcutReadyListener(shortcutInfo: ShortcutInfo)
    }

    fun prepareShortcut(station: DataRadioStation, ctx: Context, cb: ShortcutReadyListener) {
        val iconUrl = if (!station.hasIcon()) {
            Utils.resourceToUri(ctx.resources, R.drawable.ic_launcher).toString()
        } else {
            station.IconUrl
        }

        val request = ImageRequest.Builder(ctx)
            .data(iconUrl)
            .error(R.drawable.ic_launcher)
            .allowHardware(false)
            .apply {
                if (Utils.useCircularIcons(ctx)) {
                    transformations(CircleCropTransformation(), RoundedCornersTransformation(12f))
                } else {
                    transformations(RoundedCornersTransformation(12f))
                }
            }
            .target(
                onSuccess = { drawable ->
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
                    buildShortcut(ctx, station, bitmap, cb)
                },
                onError = { errorDrawable ->
                    val bitmap = (errorDrawable as? BitmapDrawable)?.bitmap ?: errorDrawable?.toBitmap()
                    if (bitmap != null) {
                        buildShortcut(ctx, station, bitmap, cb)
                    }
                }
            )
            .build()

        ctx.imageLoader.enqueue(request)
    }

    private fun buildShortcut(
        ctx: Context,
        station: DataRadioStation,
        bitmap: Bitmap,
        cb: ShortcutReadyListener
    ) {
        if (Build.VERSION.SDK_INT >= 25) {
            val intent = Intent(ActivityMain.ACTION_PLAY_STATION_BY_UUID, null, ctx, ActivityMain::class.java)
                .putExtra(ActivityMain.EXTRA_STATION_UUID, station.StationUuid)
            val shortcut = ShortcutInfo.Builder(ctx.applicationContext, "${ctx.packageName}/${station.StationUuid}")
                .setShortLabel(station.Name)
                .setIcon(Icon.createWithBitmap(bitmap))
                .setIntent(intent)
                .build()
            cb.onShortcutReadyListener(shortcut)
        }
    }
}
