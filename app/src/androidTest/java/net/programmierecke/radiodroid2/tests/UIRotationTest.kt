package net.programmierecke.radiodroid2.tests

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.UiDevice
import net.programmierecke.radiodroid2.ActivityMain
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.tests.utils.OrientationChangeAction.Companion.orientationLandscape
import net.programmierecke.radiodroid2.tests.utils.OrientationChangeAction.Companion.orientationPortrait
import net.programmierecke.radiodroid2.tests.utils.TestUtils
import net.programmierecke.radiodroid2.tests.utils.TestUtils.expectRunningNotification
import net.programmierecke.radiodroid2.tests.utils.conditionwatcher.ConditionWatcher
import net.programmierecke.radiodroid2.tests.utils.conditionwatcher.IsMusicPlayingCondition
import org.hamcrest.core.AllOf.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class UIRotationTest {

    @get:Rule
    val activityRule = ActivityTestRule(ActivityMain::class.java)

    @Before
    fun setUp() {
        TestUtils.populateFavourites(ApplicationProvider.getApplicationContext(), 5)
        TestUtils.populateHistory(ApplicationProvider.getApplicationContext(), 5)
    }

    private fun getPlayButton(): ViewInteraction =
        onView(allOf(withId(R.id.buttonPlay), isDisplayingAtLeast(80)))

    @Test
    fun stationsFragment_ShouldNotCrash_WhenScreenRotated() {
        onView(isRoot()).perform(orientationLandscape())
    }

    @Test
    fun historyFragment_ShouldNotCrash_WhenScreenRotated() {
        onView(ViewMatchers.withId(R.id.nav_item_history)).perform(ViewActions.click())
        onView(isRoot()).perform(orientationLandscape())
    }

    @Test
    fun favouritesFragment_ShouldNotCrash_WhenScreenRotated() {
        onView(withId(R.id.nav_item_starred)).perform(ViewActions.click())
        onView(isRoot()).perform(orientationLandscape())
    }

    @Test
    fun settingsFragment_ShouldNotCrash_WhenScreenRotated() {
        onView(withId(R.id.nav_item_starred)).perform(ViewActions.click())
        onView(isRoot()).perform(orientationLandscape())
    }

    @Test
    @SdkSuppress(minSdkVersion = 23)
    fun playback_ShouldNotStop_WhenScreenRotated() {
        getPlayButton().perform(ViewActions.click())

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(true), ConditionWatcher.SHORT_WAIT_POLICY)

        val orientation = activityRule.activity.resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            onView(isRoot()).perform(orientationLandscape())
        } else {
            onView(isRoot()).perform(orientationPortrait())
        }

        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.openNotification()
        expectRunningNotification(uiDevice)

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(true), ConditionWatcher.SHORT_WAIT_POLICY)
    }
}
