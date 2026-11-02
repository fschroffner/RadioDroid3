package com.github.fschroffner.floradio

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import com.github.fschroffner.floradio.cast.CastAwareActivity

class CastHandler {

    val isReal: Boolean
        get() = false

    val isCastAvailable: Boolean
        get() = false

    val isCastSessionAvailable: Boolean
        get() = false

    fun setActivity(activity: CastAwareActivity?) {
    }

    fun onCreate(context: Context) {
    }

    fun onPause() {
    }

    fun onResume() {
    }

    fun getRouteItem(context: Context, menu: Menu): MenuItem? {
        return null
    }

    fun playRemote(title: String, url: String, iconurl: String?) {
    }
}
