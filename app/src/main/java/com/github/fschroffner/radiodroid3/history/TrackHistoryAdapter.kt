package com.github.fschroffner.radiodroid3.history

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentActivity
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.databinding.ListItemHistoryTrackItemBinding

class TrackHistoryAdapter(private val activity: FragmentActivity) :
    PagingDataAdapter<TrackHistoryEntry, TrackHistoryAdapter.TrackHistoryItemViewHolder>(DIFF_CALLBACK) {

    inner class TrackHistoryItemViewHolder(val binding: ListItemHistoryTrackItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val rootview: View = binding.root
        val imageViewStationIcon: ImageView = binding.imageViewStationIcon
        val textViewTrackName: TextView = binding.textViewTrackName
        val textViewTrackArtist: TextView = binding.textViewTrackArtist
    }

    private val context: Context = activity
    private val stationImagePlaceholder: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_photo_24dp)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHistoryItemViewHolder {
        val binding = ListItemHistoryTrackItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackHistoryItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackHistoryItemViewHolder, position: Int) {
        val historyEntry = getItem(position) ?: return
        val shouldLoadIcons = Utils.shouldLoadIcons(context)

        if (shouldLoadIcons) {
            holder.imageViewStationIcon.visibility = View.VISIBLE
            if (!TextUtils.isEmpty(historyEntry.stationIconUrl)) {
                Utils.loadStationIcon(holder.imageViewStationIcon, historyEntry.stationIconUrl)
            } else {
                holder.imageViewStationIcon.setImageDrawable(stationImagePlaceholder)
            }
        } else {
            holder.imageViewStationIcon.visibility = View.GONE
        }

        holder.textViewTrackName.text = historyEntry.track
        holder.textViewTrackArtist.text = historyEntry.artist
        holder.textViewTrackName.isSelected = true
        holder.textViewTrackArtist.isSelected = true
        holder.rootview.setOnClickListener { showTrackInfoDialog(historyEntry) }
    }

    private fun showTrackInfoDialog(historyEntry: TrackHistoryEntry) {
        TrackHistoryInfoDialog(historyEntry).show(activity.supportFragmentManager, TrackHistoryInfoDialog.FRAGMENT_TAG)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TrackHistoryEntry>() {
            override fun areItemsTheSame(old: TrackHistoryEntry, new: TrackHistoryEntry) = old.uid == new.uid
            override fun areContentsTheSame(old: TrackHistoryEntry, new: TrackHistoryEntry) = old == new
        }
    }
}