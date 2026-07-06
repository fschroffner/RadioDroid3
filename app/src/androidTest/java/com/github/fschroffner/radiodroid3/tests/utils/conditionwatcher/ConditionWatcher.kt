package com.github.fschroffner.radiodroid3.tests.utils.conditionwatcher

import androidx.test.espresso.IdlingResource

/**
 * Inspired by https://github.com/AzimoLabs/ConditionWatcher
 *
 * This is alternative to [IdlingResource] when we want to wait for some unique event or
 * IdlingResource is just impractical.
 */
object ConditionWatcher {

    interface Policy {
        fun getCheckInterval(): Long

        fun getTimeoutInterval(): Long
    }

    val SHORT_WAIT_POLICY: Policy = object : Policy {
        override fun getCheckInterval(): Long = 50

        override fun getTimeoutInterval(): Long = 2000
    }

    val LONG_WAIT_POLICY: Policy = object : Policy {
        override fun getCheckInterval(): Long = 50

        override fun getTimeoutInterval(): Long = 3000
    }

    interface Condition {
        fun testCondition(): Boolean

        fun getDescription(): String
    }

    fun waitForCondition(condition: Condition, policy: Policy) {
        var elapsedTime = 0L

        while (true) {
            if (condition.testCondition()) {
                break
            } else {
                elapsedTime += policy.getCheckInterval()
                try {
                    Thread.sleep(policy.getCheckInterval())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            if (elapsedTime > policy.getTimeoutInterval()) {
                throw RuntimeException(condition.getDescription() + " - took more than " + policy.getTimeoutInterval() + " ms.")
            }
        }
    }
}
