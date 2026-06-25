package net.programmierecke.radiodroid2.tests.utils

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

object RecyclerRecyclingMatcher {
    private const val MAX_SIZE_K = 1.25f

    fun recyclerRecycles(): Matcher<View> {
        return object : TypeSafeMatcher<View>(RecyclerView::class.java) {
            override fun matchesSafely(recyclerView: View): Boolean {
                val displayMetrics = InstrumentationRegistry.getInstrumentation()
                    .targetContext.resources
                    .displayMetrics

                return !(recyclerView.measuredHeight > displayMetrics.heightPixels * MAX_SIZE_K) &&
                        !(recyclerView.measuredWidth > displayMetrics.widthPixels * MAX_SIZE_K)
            }

            override fun describeTo(description: Description) {
                description.appendText("as a Recycler does recycling of elements")
            }
        }
    }
}
