package com.github.fschroffner.radiodroid3.tests.utils

import android.content.res.Resources
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

class RecyclerViewMatcher(private val recyclerViewId: Int) {

    fun atPosition(position: Int): Matcher<View> = atPositionOnView(position, -1)

    fun atPositionOnView(position: Int, targetViewId: Int): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            private var resources: Resources? = null

            override fun describeTo(description: Description) {
                val id = if (targetViewId == -1) recyclerViewId else targetViewId
                var idDescription = id.toString()
                resources?.let {
                    idDescription = try {
                        it.getResourceName(id)
                    } catch (ex: Resources.NotFoundException) {
                        String.format("%s (resource name not found)", id)
                    }
                }
                description.appendText("with id: $idDescription")
            }

            override fun matchesSafely(view: View): Boolean {
                resources = view.resources

                val recyclerView = view.parent as? RecyclerView ?: return false
                if (recyclerView.id != recyclerViewId) return false
                if (!recyclerView.isShown) return false

                val rect = android.graphics.Rect()
                if (!recyclerView.getGlobalVisibleRect(rect) || rect.isEmpty) return false

                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                    ?: return false

                return if (targetViewId == -1) {
                    view === viewHolder.itemView
                } else {
                    val targetView = viewHolder.itemView.findViewById<View>(targetViewId)
                    view === targetView
                }
            }
        }
    }

    companion object {
        fun withRecyclerView(recyclerViewId: Int): RecyclerViewMatcher = RecyclerViewMatcher(recyclerViewId)
    }
}
