// https://stackoverflow.com/questions/29378552/in-espresso-how-to-avoid-ambiguousviewmatcherexception-when-multiple-views-matc

package net.programmierecke.radiodroid2.tests.utils

import android.view.View
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

class FirstViewMatcher : BaseMatcher<View>() {

    init {
        matchedBefore = false
    }

    override fun matches(o: Any?): Boolean {
        return if (matchedBefore) {
            false
        } else {
            matchedBefore = true
            true
        }
    }

    override fun describeTo(description: Description) {
        description.appendText(" is the first view that comes along ")
    }

    companion object {
        @JvmField
        var matchedBefore = false

        fun firstView(): Matcher<View> = FirstViewMatcher()
    }
}
