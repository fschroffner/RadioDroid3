package net.programmierecke.radiodroid2.tests.utils.conditionwatcher

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider

class IsMusicPlayingCondition(private val expectPlaying: Boolean) : ConditionWatcher.Condition {

    override fun testCondition(): Boolean {
        val manager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return manager.isMusicActive == expectPlaying
    }

    override fun getDescription(): String = ""
}
