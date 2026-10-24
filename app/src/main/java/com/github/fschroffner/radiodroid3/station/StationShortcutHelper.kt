package com.github.fschroffner.radiodroid3.station

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import jp.wasabeef.picasso.transformations.CropSquareTransformation
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils

object StationShortcutHelper {

    interface ShortcutReadyListener {
        fun onShortcutReadyListener(shortcutInfo: ShortcutInfo)
    }

    fun prepareShortcut(station: DataRadioStation, ctx: Context, cb: ShortcutReadyListener) {
        Picasso.get()
            .load(if (!station.hasIcon()) Utils.resourceToUri(ctx.resources, R.drawable.ic_launcher).toString() else station.IconUrl)
            .error(R.drawable.ic_launcher)
            .transform(if (Utils.useCircularIcons(ctx)) CropCircleTransformation() else CropSquareTransformation())
            .transform(RoundedCornersTransformation(12, 2, RoundedCornersTransformation.CornerType.ALL))
            .into(RadioIconTarget(ctx, station, cb))
    }

    private class RadioIconTarget(
        private val ctx: Context,
        private val station: DataRadioStation,
        private val cb: ShortcutReadyListener
    ) : Target {
        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
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

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable) {
            onBitmapLoaded((errorDrawable as BitmapDrawable).bitmap, null)
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
    }
}
