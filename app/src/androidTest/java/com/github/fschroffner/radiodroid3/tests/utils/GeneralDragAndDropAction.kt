package com.github.fschroffner.radiodroid3.tests.utils

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.PrecisionDescriber
import androidx.test.espresso.action.Swiper
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.util.HumanReadables
import org.hamcrest.Matcher

open class GeneralDragAndDropAction(
    private val swiper: Swiper,
    private val startCoordinatesProvider: CoordinatesProvider,
    private val endCoordinatesProvider: CoordinatesProvider,
    private val precisionDescriber: PrecisionDescriber
) : ViewAction {

    override fun getConstraints(): Matcher<View> = isDisplayingAtLeast(VIEW_DISPLAY_PERCENTAGE)

    override fun perform(uiController: UiController, view: View) {
        val startCoordinates = startCoordinatesProvider.calculateCoordinates(view)
        val endCoordinates = endCoordinatesProvider.calculateCoordinates(view)
        val precision = precisionDescriber.describePrecision()

        val events = ArrayList<MotionEvent>()
        var downEvent: MotionEvents.DownResultHolder? = null

        try {
            downEvent = MotionEvents.sendDown(uiController, startCoordinates, precision)

            val longPressTimeout = (ViewConfiguration.getLongPressTimeout() * 1.5f).toInt()
            uiController.loopMainThreadForAtLeast(longPressTimeout.toLong())

            val steps = interpolate(startCoordinates, endCoordinates, DRAG_EVENT_COUNT)

            val intervalMS = (DRAG_DURATION / steps.size).toLong()
            var eventTime = downEvent.down.downTime
            for (step in steps) {
                eventTime += intervalMS
                events.add(MotionEvents.obtainMovement(downEvent.down.downTime, eventTime, step))
            }

            eventTime += intervalMS
            events.add(
                MotionEvent.obtain(
                    downEvent.down.downTime,
                    eventTime,
                    MotionEvent.ACTION_UP,
                    endCoordinates[0],
                    endCoordinates[1],
                    0
                )
            )
            uiController.injectMotionEventSequence(events)
        } catch (e: Exception) {
            throw PerformException.Builder()
                .withActionDescription(getDescription())
                .withViewDescription(HumanReadables.describe(view))
                .withCause(e)
                .build()
        } finally {
            for (event in events) {
                event.recycle()
            }

            downEvent!!.down.recycle()
        }

        val duration = ViewConfiguration.getPressedStateDuration()
        // ensures that all work enqueued to process the swipe has been run.
        if (duration > 0) {
            uiController.loopMainThreadForAtLeast(duration.toLong())
        }
    }

    override fun getDescription(): String = swiper.toString().lowercase() + " drag-and-drop"

    companion object {
        /**
         * The minimum amount of a view that must be displayed in order to swipe across it.
         */
        private const val VIEW_DISPLAY_PERCENTAGE = 90

        /**
         * The number of motion events to send for each swipe.
         */
        private const val DRAG_EVENT_COUNT = 10

        private const val DRAG_DURATION = 300

        private fun interpolate(start: FloatArray, end: FloatArray, steps: Int): Array<FloatArray> {
            require(start.size > 1)
            require(end.size > 1)

            val res = Array(steps) { FloatArray(2) }

            for (i in 1 until steps + 1) {
                res[i - 1][0] = start[0] + (end[0] - start[0]) * i / (steps + 2f)
                res[i - 1][1] = start[1] + (end[1] - start[1]) * i / (steps + 2f)
            }

            return res
        }
    }
}
