package com.github.fschroffner.radiodroid3.tests

import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ConditionWatcher
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.IsMusicPlayingCondition
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ViewMatchWaiter.Companion.waitForView
import com.github.fschroffner.radiodroid3.tests.utils.http.MockHttpDispatcher
import okhttp3.mockwebserver.MockResponse
import org.hamcrest.Matcher
import org.hamcrest.core.AllOf.allOf
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class UIPlaybackTest {

    @get:Rule
    val activityRule = ActivityTestRule(ActivityMain::class.java)

    @Before
    fun setUp() {
        TestUtils.populateHistory(ApplicationProvider.getApplicationContext(), 1)
    }

    private fun getPlayButton(): Matcher<View> = allOf(withId(R.id.buttonPlay), isDisplayingAtLeast(80))

    @Ignore("Disabled until implemented")
    @Test
    fun error_ShouldAppear_OnStreamErrorCode() {
        (InstrumentationRegistry.getInstrumentation() as CustomTestRunner).setCustomRequestDispatcher { path ->
            if (MockHttpDispatcher.isAudioRequest.compatible(path)) {
                MockResponse().setResponseCode(404)
            } else {
                null
            }
        }

        val btnPlay = getPlayButton()

        onView(btnPlay).perform(ViewActions.click())

        waitForView(btnPlay).toMatch(withContentDescription(R.string.detail_play))

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(false), ConditionWatcher.SHORT_WAIT_POLICY)
    }

    @Test
    fun error_ShouldAppear_OnStreamPayWall() {
        (InstrumentationRegistry.getInstrumentation() as CustomTestRunner).setCustomRequestDispatcher { path ->
            if (MockHttpDispatcher.isAudioRequest.compatible(path)) {
                MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html")
            } else {
                null
            }
        }

        val btnPlay = getPlayButton()

        onView(btnPlay).perform(ViewActions.click())

        waitForView(btnPlay).toMatch(withContentDescription(R.string.detail_play))

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(false), ConditionWatcher.SHORT_WAIT_POLICY)
    }
}
