package com.github.fschroffner.radiodroid3.station

import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.github.fschroffner.radiodroid3.utils.RecyclerItemMoveAndSwipeHelper
import com.github.fschroffner.radiodroid3.utils.SwipeableViewHolder

open class ItemAdapaterContextMenuStation(
    fragmentActivity: FragmentActivity,
    resourceId: Int,
    filterType: StationsFilter.FilterType
) : ItemAdapterStation(fragmentActivity, resourceId, filterType),
    RecyclerItemMoveAndSwipeHelper.MoveAndSwipeCallback<ItemAdapterStation.StationViewHolder> {

    companion object {
        private const val TAG = "IconOnlyStation"
        private const val MIN_INTERVAL_BETWEEN_DRAG_AND_MENU_OPEN = 200L
    }

    private val DISMISS_MENU_DRAG_THRESHOLD = 0.15
    private val NEVER_IN_THE_FUTURE = Long.MAX_VALUE / 2
    private var timeLastDragEnded = 0L

    override fun onDragged(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Double, dY: Double) {
        val foregroundView = (viewHolder as SwipeableViewHolder).getForegroundView()
        val stationViewHolder = viewHolder as ItemAdapterIconOnlyStation.StationViewHolder

        if (Math.abs(dX) > foregroundView.width * DISMISS_MENU_DRAG_THRESHOLD ||
            Math.abs(dY) > foregroundView.height * DISMISS_MENU_DRAG_THRESHOLD) {
            stationViewHolder.dismissContextMenu()
        } else if (stationViewHolder.contextMenu == null) {
            if (System.currentTimeMillis() > timeLastDragEnded + MIN_INTERVAL_BETWEEN_DRAG_AND_MENU_OPEN) {
                Log.d(TAG, "Creating contextMenu from onDragged")
                stationViewHolder.onCreateContextMenu(null, foregroundView, null)
            }
        } else {
            timeLastDragEnded = NEVER_IN_THE_FUTURE
        }
    }

    override fun onMoveEnded(viewHolder: ItemAdapterStation.StationViewHolder) {
        timeLastDragEnded = System.currentTimeMillis()
        super.onMoveEnded(viewHolder)
    }
}
