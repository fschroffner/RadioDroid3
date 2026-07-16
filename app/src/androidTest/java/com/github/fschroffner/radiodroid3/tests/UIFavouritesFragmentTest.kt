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
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ActivityTestRule
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.FavouriteManager
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
class UIFavouritesFragmentTest {

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

    private lateinit var favouriteManager: FavouriteManager

    @Before
    fun setUp() {
        TestUtils.populateFavourites(ApplicationProvider.getApplicationContext(), STATIONS_COUNT)

        val app = ApplicationProvider.getApplicationContext<RadioDroidApp>()
        favouriteManager = app.favouriteManager
    }

    @Test
    fun stationsRecyclerFavourites_ShouldRecycleItems() {
        onView(ViewMatchers.withId(R.id.nav_item_starred)).perform(ViewActions.click())

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).check(matches(recyclerRecycles()))
    }

    @Ignore(
        "Disabled until drag and drop is fixed, see " +
            "https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen"
    )
    @Test
    fun stationInFavourites_ShouldBeReordered_WithDragAndDrop() {
        onView(ViewMatchers.withId(R.id.nav_item_starred)).perform(ViewActions.click())
        // 0 1 2 3 4

        onView(withId(R.id.recyclerViewStations)).perform(recyclerDragAndDrop(4, 0))
        // 4 0 1 2 3
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(0))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(4)))))
        assertEquals(getFakeRadioStationName(4), favouriteManager.getList()[0].Name)

        onView(withId(R.id.recyclerViewStations)).perform(recyclerDragAndDrop(4, 3))
        // 4 0 1 3 2
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(3))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(3)))))
        assertEquals(getFakeRadioStationName(3), favouriteManager.getList()[3].Name)
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(4))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(2)))))
        assertEquals(getFakeRadioStationName(2), favouriteManager.getList()[4].Name)

        onView(withId(R.id.recyclerViewStations)).perform(recyclerDragAndDrop(3, 1))
        // 4 3 0 1 2
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(1))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(3)))))
        assertEquals(getFakeRadioStationName(3), favouriteManager.getList()[1].Name)
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(2))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(0)))))
        assertEquals(getFakeRadioStationName(0), favouriteManager.getList()[2].Name)
    }

    @Ignore(
        "Disabled until drag and drop is fixed, see " +
            "https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen"
    )
    @Test
    fun stationInFavourites_ShouldBeReordered_WithSimpleDragAndDrop() {
        onView(ViewMatchers.withId(R.id.nav_item_starred)).perform(ViewActions.click())
        // 0 1

        onView(withId(R.id.recyclerViewStations)).perform(scrollToRecyclerItem(0))
        onView(withId(R.id.recyclerViewStations)).perform(recyclerDragAndDrop(1, 0))
        // 1 0
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(0))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(1)))))
        assertEquals(getFakeRadioStationName(1), favouriteManager.getList()[0].Name)
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(1))
            .check(matches(hasDescendant(withText(getFakeRadioStationName(0)))))
        assertEquals(getFakeRadioStationName(0), favouriteManager.getList()[1].Name)
    }

    @SdkSuppress(maxSdkVersion = 32)
    @Test
    fun stationInFavourites_ShouldBeDeleted_WithSwipeRight() {
        onView(withId(R.id.nav_item_starred)).perform(ViewActions.click())

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(0))
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(0)).perform(TestUtils.swipeRightSlow())
        waitForView(withId(com.google.android.material.R.id.snackbar_action)).toMatch(isDisplayed())
        TestUtils.dismissSnackbar()
        SystemClock.sleep(1000)
        assertEquals(STATIONS_COUNT - 1, favouriteManager.getList().size)

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(1))
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(1)).perform(TestUtils.swipeRightSlow())
        waitForView(withId(com.google.android.material.R.id.snackbar_action)).toMatch(isDisplayed())
        TestUtils.dismissSnackbar()
        SystemClock.sleep(1000)
        assertEquals(STATIONS_COUNT - 2, favouriteManager.getList().size)

        onView(allOf(withId(R.id.recyclerViewStations), FirstViewMatcher.firstView())).perform(scrollToRecyclerItem(2))
        onView(withRecyclerView(R.id.recyclerViewStations).atPosition(2)).perform(TestUtils.swipeRightSlow())
        waitForView(withId(com.google.android.material.R.id.snackbar_action)).toMatch(isDisplayed())
        SystemClock.sleep(1000)
        assertEquals(STATIONS_COUNT - 3, favouriteManager.getList().size)

        // Snackbar with undo action
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            // for whatever reason this often does not work on API 21 emulators
            onView(withId(com.google.android.material.R.id.snackbar_action)).perform(ViewActions.click())

            assertEquals(STATIONS_COUNT - 2, favouriteManager.getList().size)
            onView(withId(R.id.recyclerViewStations)).perform(scrollToRecyclerItem(2))
            onView(withRecyclerView(R.id.recyclerViewStations).atPosition(2))
                .check(matches(hasDescendant(withText(getFakeRadioStationName(4)))))
        }
    }

    companion object {
        private const val STATIONS_COUNT = 20

        @JvmStatic
        @Parameterized.Parameters(name = "orientation={0}")
        fun initParameters(): Iterable<Array<Any>> = listOf(
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
            arrayOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        )
    }
}
