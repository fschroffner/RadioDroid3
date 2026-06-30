package net.programmierecke.radiodroid2.tests.utils

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
            private var childView: View? = null

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

                if (childView == null) {
                    val recyclerView = view.rootView.findViewById<RecyclerView>(recyclerViewId)
                    if (recyclerView != null) {
                        childView = recyclerView.findViewHolderForAdapterPosition(position)!!.itemView
                    } else {
                        return false
                    }
                }

                return if (targetViewId == -1) {
                    view === childView
                } else {
                    val targetView = childView!!.findViewById<View>(targetViewId)
                    view === targetView
                }
            }
        }
    }

    companion object {
        fun withRecyclerView(recyclerViewId: Int): RecyclerViewMatcher = RecyclerViewMatcher(recyclerViewId)
    }
}
