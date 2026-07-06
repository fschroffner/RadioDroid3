package com.github.fschroffner.radiodroid3.tests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils.expectNoNotification
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils.expectRunningNotification
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.ConditionWatcher
import com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher.IsMusicPlayingCondition
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
// UI notifications currently only work with API 23+
// On API 33+ POST_NOTIFICATIONS is a runtime permission; CustomTestRunner
// pre-grants it before the tests run, so no user-choice dialog appears.
@SdkSuppress(minSdkVersion = 23)
class UINotificationTests {

    @get:Rule
    val activityRule = ActivityTestRule(ActivityMain::class.java)

    @Before
    fun setUp() {
        TestUtils.populateHistory(ApplicationProvider.getApplicationContext(), 1)
    }

    private fun getPlayButton(): ViewInteraction =
        onView(allOf(withId(R.id.buttonPlay), isDisplayingAtLeast(80)))

    private fun launchPausedNotification() {
        val btnPlay = getPlayButton()
        btnPlay.perform(ViewActions.click(), ViewActions.click())
    }

    private fun launchPlayingNotification() {
        val btnPlay = getPlayButton()
        btnPlay.perform(ViewActions.click())
    }

    private class UIObjectWaiterCondition(
        private val uiDevice: UiDevice,
        private val bySelector: BySelector
    ) : ConditionWatcher.Condition {

        override fun testCondition(): Boolean = uiDevice.findObject(bySelector) != null

        override fun getDescription(): String = "Wait for any view to match $bySelector"
    }

    private fun waitForObject(uiDevice: UiDevice, bySelector: BySelector) {
        ConditionWatcher.waitForCondition(
            UIObjectWaiterCondition(uiDevice, bySelector),
            ConditionWatcher.SHORT_WAIT_POLICY
        )
    }

    // The play/pause button is the standard Media3 transport control rendered by
    // DefaultMediaNotificationProvider, so its content description comes from Media3's
    // own resources ("Play" when paused, "Pause" when playing) rather than the app's
    // action_resume/action_pause strings. The custom stop button keeps the app string.
    private fun playButtonDesc(): String =
        appString(androidx.media3.session.R.string.media3_controls_play_description)

    private fun pauseButtonDesc(): String =
        appString(androidx.media3.session.R.string.media3_controls_pause_description)

    @Ignore
    @Test
    fun playback_ShouldStart_OnResumeFromNotification() {
        launchPausedNotification()

        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.openNotification()

        expectRunningNotification(uiDevice)
        uiDevice.wait(Until.hasObject(By.desc(playButtonDesc())), 2000)

        val resumeBtn: UiObject2? = uiDevice.findObject(By.desc(playButtonDesc()))
        assertNotNull(resumeBtn)

        resumeBtn!!.click()

        waitForObject(uiDevice, By.desc(pauseButtonDesc()))

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(true), ConditionWatcher.SHORT_WAIT_POLICY)
    }

    @Test
    fun playback_ShouldPause_OnPauseFromNotification() {
        launchPlayingNotification()

        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.openNotification()

        expectRunningNotification(uiDevice)
        uiDevice.wait(Until.hasObject(By.desc(pauseButtonDesc())), 250)

        val pauseBtn: UiObject2? = uiDevice.findObject(By.desc(pauseButtonDesc()))
        assertNotNull(pauseBtn)

        pauseBtn!!.click()

        waitForObject(uiDevice, By.desc(playButtonDesc()))

        ConditionWatcher.waitForCondition(IsMusicPlayingCondition(false), ConditionWatcher.SHORT_WAIT_POLICY)
    }

    @Test
    fun notification_ShouldDisappear_OnStopFromNotification() {
        launchPlayingNotification()

        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiDevice.openNotification()

        expectRunningNotification(uiDevice)
        uiDevice.wait(Until.hasObject(By.desc(appString(R.string.action_stop))), 250)

        val stopBtn: UiObject2? = uiDevice.findObject(By.desc(appString(R.string.action_stop)))
        if (stopBtn != null) { // there might be no stop button
            stopBtn.click()

            expectNoNotification(uiDevice)

            ConditionWatcher.waitForCondition(IsMusicPlayingCondition(false), ConditionWatcher.SHORT_WAIT_POLICY)
        }
    }

    private fun appString(resId: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resId)
}
