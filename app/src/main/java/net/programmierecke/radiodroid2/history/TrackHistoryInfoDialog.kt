package net.programmierecke.radiodroid2.history

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import net.programmierecke.radiodroid2.R
import java.text.DateFormat

class TrackHistoryInfoDialog(private val historyEntry: TrackHistoryEntry) : BottomSheetDialogFragment() {

    @Nullable
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        retainInstance = true

        val view = inflater.inflate(R.layout.dialog_track_history_details, container, false)

        val imageViewTrackArt = view.findViewById<AppCompatImageView>(R.id.imageViewTrackArt)
        val textViewDate = view.findViewById<TextView>(R.id.textViewDate)
        val textViewDuration = view.findViewById<TextView>(R.id.textViewDuration)
        val btnLyrics = view.findViewById<AppCompatButton>(R.id.btnViewLyrics)
        val btnCopyInfo = view.findViewById<AppCompatButton>(R.id.btnCopyTrackInfo)

        val resource = requireContext().resources
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, resource.displayMetrics).toInt()
        Picasso.get()
            .load(historyEntry.artUrl)
            .placeholder(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_photo_24dp)!!)
            .resize(px, 0)
            .into(imageViewTrackArt)

        textViewDate.text = DateFormat.getDateInstance().format(historyEntry.startTime)

        if (historyEntry.endTime.after(historyEntry.startTime)) {
            textViewDuration.text = DateUtils.formatElapsedTime((historyEntry.endTime.time - historyEntry.startTime.time) / 1000)
        } else {
            textViewDuration.text = ""
        }

        btnLyrics.setOnClickListener {
            if (isQuickLyricInstalled()) {
                requireContext().startActivity(Intent("com.geecko.QuickLyric.getLyrics")
                    .putExtra("TAGS", arrayOf(historyEntry.artist, historyEntry.track)))
            } else {
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.alert_install_lyrics_app))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        try {
                            requireContext().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.geecko.QuickLyric")))
                        } catch (_: ActivityNotFoundException) {
                            try {
                                requireContext().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.geecko.QuickLyric")))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(requireContext(), R.string.notify_open_link_failure, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            }
        }

        btnCopyInfo.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Track info", "${historyEntry.artist} ${historyEntry.track}"))
                Toast.makeText(requireContext().applicationContext, requireContext().resources.getText(R.string.notify_track_info_copied), Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun isQuickLyricInstalled(): Boolean {
        return try {
            requireContext().packageManager.getApplicationInfo("com.geecko.QuickLyric", 0).enabled
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        const val FRAGMENT_TAG = "tracks_history_info_dialog_fragment"
    }
}
