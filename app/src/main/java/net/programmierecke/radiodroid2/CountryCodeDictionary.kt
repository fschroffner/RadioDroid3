package net.programmierecke.radiodroid2

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.Locale

class CountryCodeDictionary private constructor() {
    private data class Country(val name: String, val code: String)

    private val codeToCountry = HashMap<String, String>()

    fun load(context: Context) {
        val inputStream = context.resources.openRawResource(R.raw.countries)
        val type = object : TypeToken<Collection<Country>>() {}.type
        val countries: Collection<Country> = Gson().fromJson(InputStreamReader(inputStream), type)
        countries.forEach { codeToCountry[it.code.lowercase(Locale.ENGLISH)] = it.name }
    }

    fun getCountryByCode(code: String): String? = codeToCountry[code.lowercase(Locale.ENGLISH)]

    companion object {
        @JvmStatic
        val instance: CountryCodeDictionary = CountryCodeDictionary()
    }
}
