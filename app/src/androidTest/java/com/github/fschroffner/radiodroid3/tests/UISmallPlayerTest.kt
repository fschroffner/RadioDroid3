package com.github.fschroffner.radiodroid3.tests

import android.content.Context
import android.media.AudioManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.rule.ActivityTestRule
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerViewMatcher.Companion.withRecyclerView
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ConditionWatcher
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.IsMusicPlayingCondition
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ViewMatchWaiter.Companion.waitForView
import org.hamcrest.Matcher
import org.hamcrest.core.AllOf.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class UISmallPlayerTest {

    @get:Rule
    val activityRule = ActivityTestRule(ActivityMain::class.java)

    @Before
    fun setUp() {
        TestUtils.populateHistory(ApplicationProvider.getApplicationContext(), 1)
    }

    private fun getPlayButton(): Matcher<View> = allOf(withId(R.id.buttonPlay), isDisplayingAtLeast(80))

    private fun isMusicPlaying(): Boolean {
        val manager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return manager.isMusicActive
    }

    @Test
    fun stationListItem_ShouldStartPlayBack_WhenClicked() {
        onView(allOf(withId(R.id.layoutMain), isDescendantOfA(withRecyclerView(R.id.recyclerViewStations).atPosition(0)))).perform(ViewActions.click())

        onView(getPlayButton()).check(matches(withContentDescription(R.string.detail_pause)))

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(true), ConditionWatcher.SHORT_WAIT_POLICY)
    }

    @SdkSuppress(maxSdkVersion = 32)
    @Test
    fun playBackState_ShouldBeCorrect_AfterRapidToggling() {
        // TODO: Make clicking more rapid. there is a visible delay as of now.

        val btnPlay = getPlayButton()
        for (i in 0 until 7) {
            onView(btnPlay).perform(ViewActions.click())
        }

        waitForView(btnPlay).toMatch(withContentDescription(R.string.detail_pause))

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(true), ConditionWatcher.SHORT_WAIT_POLICY)

        for (i in 0 until 7) {
            onView(btnPlay).perform(ViewActions.click())
        }

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(false), ConditionWatcher.SHORT_WAIT_POLICY)
    }
}
