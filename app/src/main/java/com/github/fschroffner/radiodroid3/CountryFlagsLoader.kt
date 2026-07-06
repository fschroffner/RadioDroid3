package com.github.fschroffner.radiodroid3

import android.content.Context
import android.graphics.drawable.Drawable

class CountryFlagsLoader private constructor() {
    fun getFlag(context: Context, countryCode: String?): Drawable? {
        countryCode ?: return null
        val resourceId = context.resources.getIdentifier("flag_${countryCode.lowercase()}", "drawable", context.packageName)
        return if (resourceId != 0) context.resources.getDrawable(resourceId) else null
    }

    companion object {
        @JvmStatic
        val instance: CountryFlagsLoader = CountryFlagsLoader()
    }
}
