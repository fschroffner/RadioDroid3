package com.github.fschroffner.radiodroid3.tests.utils

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.PrecisionDescriber
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Swiper
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.util.HumanReadables
import org.hamcrest.Matcher

class RecyclerDragAndDropAction(
    private val swiper: Swiper,
    private val idxFrom: Int,
    private val idxTo: Int,
    private val precisionDescriber: PrecisionDescriber
) : ViewAction {

    override fun getConstraints(): Matcher<View> = isDisplayingAtLeast(VIEW_DISPLAY_PERCENTAGE)

    override fun perform(uiController: UiController, view: View) {
        val recyclerView = view as RecyclerView

        TestUtils.centerItemInRecycler(uiController, recyclerView, idxFrom)
        uiController.loopMainThreadUntilIdle()

        val fromView = recyclerView.findViewHolderForAdapterPosition(idxFrom)!!.itemView
        val fromViewPosition = GeneralLocation.VISIBLE_CENTER.calculateCoordinates(fromView)

        val precision = precisionDescriber.describePrecision()

        val events = ArrayList<MotionEvent>()
        var downEvent: MotionEvents.DownResultHolder? = null

        var success = false
        var i = 0
        while (i < 3 && !success) {
            try {
                downEvent = MotionEvents.sendDown(uiController, fromViewPosition, precision)

                val longPressTimeout = (ViewConfiguration.getLongPressTimeout() * 1.5f).toInt()
                uiController.loopMainThreadForAtLeast(longPressTimeout.toLong())

                TestUtils.centerItemInRecycler(uiController, recyclerView, idxTo)
                uiController.loopMainThreadUntilIdle()

                val toView = recyclerView.findViewHolderForAdapterPosition(idxTo)!!.itemView
                val toViewPosition = GeneralLocation.TOP_CENTER.calculateCoordinates(toView)

                val steps = interpolate(fromViewPosition, toViewPosition, DRAG_EVENT_COUNT)

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
                        toViewPosition[0],
                        toViewPosition[1],
                        0
                    )
                )
                uiController.injectMotionEventSequence(events)
                success = true
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

                downEvent?.down?.recycle()
            }
            i++
        }

        val duration = ViewConfiguration.getPressedStateDuration()
        // ensures that all work enqueued to process the swipe has been run.
        if (duration > 0) {
            uiController.loopMainThreadForAtLeast(duration.toLong())
        }
    }

    override fun getDescription(): String = swiper.toString().lowercase() + " recycler-drag-and-drop"

    companion object {
        /**
         * The minimum amount of a view that must be displayed in order to swipe across it.
         */
        private const val VIEW_DISPLAY_PERCENTAGE = 50

        /**
         * The number of motion events to send for each swipe.
         */
        private const val DRAG_EVENT_COUNT = 10

        private const val DRAG_DURATION = 600

        fun recyclerDragAndDrop(idxFrom: Int, idxTo: Int): ViewAction =
            RecyclerDragAndDropAction(Swipe.FAST, idxFrom, idxTo, Press.FINGER)

        private fun interpolate(start: FloatArray, end: FloatArray, steps: Int): Array<FloatArray> {
            require(start.size > 1)
            require(end.size > 1)

            val res = Array(steps) { FloatArray(2) }

            for (i in 1 until steps + 1) {
                res[i - 1][0] = start[0] + (end[0] - start[0]) * i / steps
                res[i - 1][1] = start[1] + (end[1] - start[1]) * i / steps
            }

            return res
        }
    }
}
