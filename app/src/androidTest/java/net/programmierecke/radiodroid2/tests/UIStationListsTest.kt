package net.programmierecke.radiodroid2.tests

import android.content.pm.ActivityInfo
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.viewpager.widget.ViewPager
import net.programmierecke.radiodroid2.ActivityMain
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.tests.utils.RecyclerRecyclingMatcher.recyclerRecycles
import net.programmierecke.radiodroid2.tests.utils.RecyclerViewItemCountAssertion.Companion.withItemCount
import net.programmierecke.radiodroid2.tests.utils.ViewPagerIdlingResource
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
class UIStationListsTest {

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

    @Test
    fun mainActivity_ShouldNotCrash_WhenLaunched() {
        onView(ViewMatchers.withId(R.id.my_awesome_toolbar)).check(matches(hasDescendant(withText(R.string.nav_item_stations))))
    }

    @Test
    fun stationTabs_DoWork_WhenSwiped() {
        val idlingResource = ViewPagerIdlingResource(activityRule.activity.findViewById<ViewPager>(R.id.viewpager), "ViewPager")
        IdlingRegistry.getInstance().register(idlingResource)

        onView(allOf(withId(R.id.recyclerViewStations), isDisplayingAtLeast(60)))
            .check(withItemCount(greaterThan(0)))
            .check(matches(recyclerRecycles()))

        for (i in 0 until 7) {
            // TODO: We cannot swipe on containerView because it is displayed by less than 90%,
            //       which breaks constraint in swipeLeft().
            onView(withId(R.id.main_content)).perform(ViewActions.swipeLeft())
            onView(allOf(withId(R.id.recyclerViewStations), isDisplayingAtLeast(60)))
                .check(withItemCount(greaterThan(0)))
                .check(matches(recyclerRecycles()))
        }

        onView(withId(R.id.main_content)).perform(ViewActions.swipeLeft())

        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "orientation={0}")
        fun initParameters(): Iterable<Array<Any>> = listOf(
            arrayOf<Any>(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
            arrayOf<Any>(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        )
    }
}
