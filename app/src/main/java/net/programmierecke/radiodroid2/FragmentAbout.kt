package net.programmierecke.radiodroid2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class FragmentAbout : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.layout_about, null)

        val textVersion = view.findViewById<TextView>(R.id.about_version)
        if (textVersion != null) {
            var version = BuildConfig.VERSION_NAME
            val gitHash = getString(R.string.GIT_HASH)
            val buildDate = getString(R.string.BUILD_DATE)

            if (gitHash.isNotEmpty()) version += " (git $gitHash)"
            textVersion.text = resources.getString(R.string.about_version, "$version $buildDate")
        }

        return view
    }
}
