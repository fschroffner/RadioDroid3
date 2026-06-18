package net.programmierecke.radiodroid2.history

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
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.Utils
import net.programmierecke.radiodroid2.service.PlayerServiceUtil

class TrackHistoryAdapter(private val activity: FragmentActivity) :
    PagedListAdapter<TrackHistoryEntry, TrackHistoryAdapter.TrackHistoryItemViewHolder>(DIFF_CALLBACK) {

    inner class TrackHistoryItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rootview: View = itemView
        val imageViewStationIcon: ImageView = itemView.findViewById(R.id.imageViewStationIcon)
        val textViewTrackName: TextView = itemView.findViewById(R.id.textViewTrackName)
        val textViewTrackArtist: TextView = itemView.findViewById(R.id.textViewTrackArtist)
    }

    private val context: Context = activity
    private val inflater = LayoutInflater.from(context)
    private var shouldLoadIcons = false
    private val stationImagePlaceholder: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_photo_24dp)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHistoryItemViewHolder {
        return TrackHistoryItemViewHolder(inflater.inflate(R.layout.list_item_history_track_item, parent, false))
    }

    override fun onBindViewHolder(holder: TrackHistoryItemViewHolder, position: Int) {
        val historyEntry = getItem(position) ?: return

        if (shouldLoadIcons) {
            if (!TextUtils.isEmpty(historyEntry.stationIconUrl)) {
                PlayerServiceUtil.getStationIcon(holder.imageViewStationIcon, historyEntry.stationIconUrl)
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

    override fun submitList(pagedList: PagedList<TrackHistoryEntry>?) {
        shouldLoadIcons = Utils.shouldLoadIcons(context)
        super.submitList(pagedList)
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
