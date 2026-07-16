package com.github.fschroffner.radiodroid3.tests

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ActivityTestRule
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.HistoryManager
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.tests.utils.FirstViewMatcher
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerDragAndDropAction.Companion.recyclerDragAndDrop
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerRecyclingMatcher.recyclerRecycles
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerViewMatcher.Companion.withRecyclerView
import com.github.fschroffner.radiodroid3.tests.utils.ScrollToRecyclerItemAction.Companion.scrollToRecyclerItem
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils.getFakeRadioStationName
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ViewMatchWaiter.Companion.waitForView
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
class UIHistoryFragmentTest {

    @JvmField
    @Parameterized.Parameter(0)
    var orientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    @get:Rule
    val activityRule = object : ActivityTestRule<ActivityMain>(ActivityMain::class.java) {
        override fun afterActivityLaunched() {
            activity.requestedOrientation = orientation
            super.afterActivityLaunched()
        }
    }

    private lateinit var historyManager: HistoryManager

    @Before
    fun setUp() {
        TestUtils.populateHistory(ApplicationProvider.getApplicationContext(), STATIONS_COUNT)

        val app = ApplicationProvider.getApplicationContext<RadioDroidApp>()
        historyManager = app.historyManager
    }

    @Test
    fun stationsRecyclerHistory_ShouldRecycleItems() {
        onView(ViewMatchers.withId(R.id.nav_item_starred)).perform(ViewActions.click())

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).check(matches(recyclerRecycles()))
    }

    @Ignore
    @Test
    fun stationsInHistory_ShouldNotBeReordered_WithDragAndDrop() {
        onView(ViewMatchers.withId(R.id.nav_item_history)).perform(ViewActions.click())

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(0))
        onView(withId(R.id.recyclerViewStations)).perform(recyclerDragAndDrop(1, 0))

        for (i in 0 until 5) {
            onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(i))
            onView(withRecyclerView(R.id.recyclerViewStations).atPosition(i))
                .check(matches(hasDescendant(withText(getFakeRadioStationName(STATIONS_COUNT - i - 1)))))
            assertEquals(historyManager.getList()[i].Name, getFakeRadioStationName(STATIONS_COUNT - i - 1))
        }
    }

    @SdkSuppress(maxSdkVersion = 32)
    @Test
    fun stationInHistory_ShouldBeDeleted_WithSwipeRight() {
        onView(withId(R.id.nav_item_history)).perform(ViewActions.click())

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(0))
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(0)).perform(TestUtils.swipeRightSlow())
        waitForView(withId(com.google.android.material.R.id.snackbar_action)).toMatch(isDisplayed())
        SystemClock.sleep(1000)
        assertEquals(STATIONS_COUNT - 1, historyManager.getList().size)

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(1))
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(1)).perform(TestUtils.swipeRightSlow())
        waitForView(withId(com.google.android.material.R.id.snackbar_action)).toMatch(isDisplayed())
        SystemClock.sleep(1000)
        assertEquals(STATIONS_COUNT - 2, historyManager.getList().size)

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(2))
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(2)).perform(TestUtils.swipeRightSlow())
        waitForView(withId(com.google.android.material.R.id.snackbar_action)).toMatch(isDisplayed())
        SystemClock.sleep(1000)
        assertEquals(STATIONS_COUNT - 3, historyManager.getList().size)

        // Snackbar with undo action
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            // for whatever reason this often does not work on API 21 emulators
            onView(withId(com.google.android.material.R.id.snackbar_action)).perform(ViewActions.click())

            assertEquals(STATIONS_COUNT - 2, historyManager.getList().size)
            onView(withId(R.id.recyclerViewStations)).perform(scrollToRecyclerItem(2))
            onView(withRecyclerView(R.id.recyclerViewStations).atPosition(2))
                .check(matches(hasDescendant(withText(getFakeRadioStationName(STATIONS_COUNT - 5)))))
        }
    }

    companion object {
        private const val STATIONS_COUNT = 20

        @JvmStatic
        @Parameterized.Parameters(name = "orientation={0}")
        fun initParameters(): Iterable<Array<Any>> = listOf(
            arrayOf<Any>(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
            arrayOf<Any>(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        )
    }
}
