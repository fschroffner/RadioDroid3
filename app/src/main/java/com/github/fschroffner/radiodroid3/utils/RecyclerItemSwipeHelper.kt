package com.github.fschroffner.radiodroid3.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils

open class RecyclerItemSwipeHelper<ViewHolderType : SwipeableViewHolder>(
    context: Context,
    dragDirs: Int,
    swipeDirs: Int,
    private val swipeListener: SwipeCallback<ViewHolderType>
) : ItemTouchHelper.SimpleCallback(dragDirs, swipeDirs) {

    interface SwipeCallback<ViewHolderType> {
        fun onSwiped(viewHolder: ViewHolderType, direction: Int)
    }

    private val swipeToDeleteIsEnabled = (swipeDirs and ItemTouchHelper.LEFT) > 0 || (swipeDirs and ItemTouchHelper.RIGHT) > 0
    private val background = ColorDrawable(Utils.themeAttributeToColor(R.attr.swipeDeleteBackgroundColor, context, Color.RED))
    private val icon: IconicsDrawable? = if (swipeToDeleteIsEnabled) {
        IconicsDrawable(context, GoogleMaterial.Icon.gmd_delete_sweep)
            .size(IconicsSize.dp(48))
            .color(IconicsColor.colorInt(Utils.themeAttributeToColor(R.attr.swipeDeleteIconColor, context, Color.WHITE)))
    } else null

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        viewHolder?.let { getDefaultUIUtil().onSelected((it as SwipeableViewHolder).getForegroundView()) }
    }

    override fun onChildDrawOver(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        getDefaultUIUtil().onDrawOver(c, recyclerView, (viewHolder as SwipeableViewHolder).getForegroundView(), dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        getDefaultUIUtil().clearView((viewHolder as SwipeableViewHolder).getForegroundView())
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        val foregroundView = (viewHolder as SwipeableViewHolder).getForegroundView()
        if (swipeToDeleteIsEnabled) drawSwipeToDeleteBackground(c, viewHolder.itemView, dX, dY)
        getDefaultUIUtil().onDraw(c, recyclerView, foregroundView, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawSwipeToDeleteBackground(c: Canvas, itemView: View, dX: Float, dY: Float) {
        val ic = icon ?: return
        val iconMargin = (itemView.height - ic.intrinsicHeight) / 2
        val iconTop = itemView.top + iconMargin
        val iconBottom = iconTop + ic.intrinsicHeight

        when {
            dX > 0 -> {
                var iconLeft = itemView.left + iconMargin
                var iconRight = iconLeft + ic.intrinsicWidth
                val constraint = if (itemView.left + dX.toInt() < iconRight + iconMargin) dX.toInt() - ic.intrinsicWidth - iconMargin * 2 else 0
                iconLeft += constraint; iconRight += constraint
                ic.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
            }
            dX < 0 -> {
                var iconRight = itemView.right - iconMargin
                var iconLeft = iconRight - ic.intrinsicWidth
                val constraint = if (itemView.right + dX.toInt() > iconLeft - iconMargin) ic.intrinsicWidth + iconMargin * 2 + dX.toInt() else 0
                iconLeft += constraint; iconRight += constraint
                ic.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                background.setBounds(itemView.right, itemView.top, itemView.right + dX.toInt(), itemView.bottom)
            }
            else -> { ic.setBounds(0, 0, 0, 0); background.setBounds(0, 0, 0, 0) }
        }
        background.draw(c)
        ic.draw(c)
    }

    override fun isLongPressDragEnabled() = false
    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

    @Suppress("UNCHECKED_CAST")
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        swipeListener.onSwiped(viewHolder as ViewHolderType, direction)
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float) = 1f
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.35f
}
