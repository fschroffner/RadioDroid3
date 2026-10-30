package com.github.fschroffner.radiodroid3

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.fschroffner.radiodroid3.databinding.LayoutAboutBinding

class FragmentAbout : Fragment() {
    private var _binding: LayoutAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutAboutBinding.inflate(inflater, container, false)

        var version = BuildConfig.VERSION_NAME
        val gitHash = getString(R.string.GIT_HASH)
        val buildDate = getString(R.string.BUILD_DATE)

        if (gitHash.isNotEmpty()) version += " (git $gitHash)"
        binding.aboutVersion.text = resources.getString(R.string.about_version, "$version $buildDate")

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
