package net.programmierecke.radiodroid2.tests.utils

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.UiController
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.programmierecke.radiodroid2.BuildConfig
import net.programmierecke.radiodroid2.R
import net.programmierecke.radiodroid2.RadioDroidApp
import net.programmierecke.radiodroid2.station.DataRadioStation
import net.programmierecke.radiodroid2.tests.utils.conditionwatcher.ConditionWatcher

object TestUtils {

    init {
        BuildConfig.IS_TESTING.set(true)
    }

    fun getFakeRadioStationName(idx: Int): String = String.format("Test Station %d", idx)

    fun generateFakeRadioStation(idx: Int): DataRadioStation {
        val station = DataRadioStation()

        val uuid = String.format("8fb6e56c-155c-4d70-aa72-2322e640c2d3-%d", idx)

        station.Name = getFakeRadioStationName(idx)
        station.StationUuid = uuid
        station.ChangeUuid = uuid
        station.StreamUrl = ""
        station.HomePageUrl = ""
        station.IconUrl = ""
        station.Country = "Angola"
        station.State = ""
        station.TagsAll = "Tag1,Tag2,Tag3"
        station.Language = "English"
        station.ClickCount = 100
        station.ClickTrend = 1
        station.Votes = 110
        station.RefreshRetryCount = 0
        station.Bitrate = 128
        station.Codec = "mp3"
        station.Working = true
        station.Hls = false
        return station
    }

    fun populateFavourites(context: Context, stationsCount: Int) {
        val app = context.applicationContext as RadioDroidApp
        val favouriteManager = app.favouriteManager
        favouriteManager.clear()

        for (i in 0 until stationsCount) {
            favouriteManager.add(generateFakeRadioStation(i))
        }
    }

    fun populateHistory(context: Context, stationsCount: Int) {
        val app = context.applicationContext as RadioDroidApp
        val historyManager = app.historyManager
        historyManager.clear()

        for (i in 0 until stationsCount) {
            historyManager.add(generateFakeRadioStation(i))
        }
    }

    /**
     * Tries make item to be in a center of RecyclerView.
     *
     * @param recyclerView the recycler view containing the item
     * @param idx          index of the item in adapter
     */
    fun centerItemInRecycler(uiController: UiController, recyclerView: RecyclerView, idx: Int) {
        recyclerView.scrollToPosition(idx)
        uiController.loopMainThreadUntilIdle()

        val itemView = recyclerView.findViewHolderForAdapterPosition(idx)!!.itemView

        val originalPos = IntArray(2)

        itemView.getLocationInWindow(originalPos)

        val scrollX = (originalPos[0] - (recyclerView.x + recyclerView.width / 2)).toInt()
        val scrollY = (originalPos[1] - (recyclerView.y + recyclerView.height / 2)).toInt()

        recyclerView.scrollBy(scrollX, scrollY)
        uiController.loopMainThreadUntilIdle()
    }

    fun expectRunningNotification(uiDevice: UiDevice) {
        val expectedAppName = ApplicationProvider.getApplicationContext<Context>().getString(R.string.app_name)
        uiDevice.wait(Until.hasObject(By.textStartsWith(expectedAppName)), 250)
    }

    fun expectNoNotification(uiDevice: UiDevice) {
        val expectedAppName = ApplicationProvider.getApplicationContext<Context>().getString(R.string.app_name)

        ConditionWatcher.waitForCondition(object : ConditionWatcher.Condition {
            override fun testCondition(): Boolean {
                return uiDevice.findObject(By.textStartsWith(expectedAppName)) == null
            }

            override fun getDescription(): String = "Wait for notification to disappear"
        }, ConditionWatcher.SHORT_WAIT_POLICY)
    }
}
