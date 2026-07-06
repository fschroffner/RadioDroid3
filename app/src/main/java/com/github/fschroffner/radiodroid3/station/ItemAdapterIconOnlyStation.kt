package com.github.fschroffner.radiodroid3.station

import android.util.TypedValue
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.zawadz88.materialpopupmenu.MaterialPopupMenu
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.service.PlayerServiceUtil
import com.github.fschroffner.radiodroid3.utils.RecyclerItemMoveAndSwipeHelper
import com.github.fschroffner.radiodroid3.utils.SwipeableViewHolder

class ItemAdapterIconOnlyStation(
    fragmentActivity: FragmentActivity,
    resourceId: Int,
    filterType: StationsFilter.FilterType
) : ItemAdapaterContextMenuStation(fragmentActivity, resourceId, filterType),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    inner class StationViewHolder(itemView: View) :
        ItemAdapterStation.StationViewHolder(itemView),
        View.OnClickListener,
        View.OnCreateContextMenuListener,
        SwipeableViewHolder {

        var contextMenu: MaterialPopupMenu? = null

        init {
            viewForeground = itemView.findViewById(R.id.station_icon_foreground)
            frameLayout = itemView.findViewById(R.id.stationIconFrameLayout)
            imageViewIcon = itemView.findViewById(R.id.iconImageViewIcon)
            transparentImageView = itemView.findViewById(R.id.iconTransparentCircle)
            itemView.setOnCreateContextMenuListener(this)
        }

        fun dismissContextMenu() {
            contextMenu?.dismiss()
            contextMenu = null
        }

        override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            if (contextMenu != null) return
            val pos = adapterPosition
            val station = filteredStationsList[pos]
            contextMenu = StationPopupMenu.open(v!!, context, activity, station, this@ItemAdapterIconOnlyStation)
            contextMenu!!.setOnDismissListener {
                dismissContextMenu()
                null
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(resourceId, parent, false)
        return StationViewHolder(v)
    }

    override fun onBindViewHolder(holder: ItemAdapterStation.StationViewHolder, position: Int) {
        val station = filteredStationsList[position]
        val useCircularIcons = Utils.useCircularIcons(context)

        if (station.hasIcon()) {
            setupIcon(useCircularIcons, holder.imageViewIcon, holder.transparentImageView)
            PlayerServiceUtil.getStationIcon(holder.imageViewIcon, station.IconUrl)
        } else {
            holder.imageViewIcon.setImageDrawable(stationImagePlaceholder)
        }

        val tv = TypedValue()
        if (playingStationPosition == position) {
            context.theme.resolveAttribute(R.attr.colorAccentMy, tv, true)
            holder.frameLayout.setBackgroundColor(tv.data)
            holder.transparentImageView.setColorFilter(tv.data)
        } else {
            context.theme.resolveAttribute(R.attr.boxBackgroundColor, tv, true)
            holder.frameLayout.setBackgroundColor(tv.data)
        }
    }

    fun enableItemMove(recyclerView: RecyclerView) {
        val helper = RecyclerItemMoveAndSwipeHelper<ItemAdapterStation.StationViewHolder>(
            context,
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0, this
        )
        ItemTouchHelper(helper).attachToRecyclerView(recyclerView)
    }
}
