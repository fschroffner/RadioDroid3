package com.github.fschroffner.radiodroid3.tests

import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.viewpager.widget.ViewPager
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.tests.utils.ClickableSpanViewAction.Companion.clickClickableSpan
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerViewItemCountAssertion.Companion.withItemCount
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerViewMatcher.Companion.withRecyclerView
import com.github.fschroffner.radiodroid3.tests.utils.ViewPagerIdlingResource
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ViewMatchWaiter.Companion.waitForView
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.core.AllOf.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class UIStationTagsTest {

    @get:Rule
    val activityRule = ActivityTestRule(ActivityMain::class.java)

    @Test
    fun stationTag_ShouldSearchStationsByTag_WhenClicked() {
        val idlingResource = ViewPagerIdlingResource(activityRule.activity.findViewById<ViewPager>(R.id.viewpager), "ViewPager")
        IdlingRegistry.getInstance().register(idlingResource)

        onView(allOf(withId(R.id.buttonMore), isDescendantOfA(withRecyclerView(R.id.recyclerViewStations).atPosition(0)))).perform(ViewActions.click())
        onView(allOf(withId(R.id.viewTags), isDescendantOfA(withRecyclerView(R.id.recyclerViewStations).atPosition(0)))).perform(clickClickableSpan(0))

        waitForView(allOf(withId(R.id.recyclerViewStations), isDisplayingAtLeast(90)))
            .toCheck(withItemCount(greaterThan(0)))

        IdlingRegistry.getInstance().unregister(idlingResource)
    }
}
