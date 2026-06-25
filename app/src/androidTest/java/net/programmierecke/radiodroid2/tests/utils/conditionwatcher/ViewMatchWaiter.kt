package net.programmierecke.radiodroid2.tests.utils.conditionwatcher

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.assertion.ViewAssertions.matches
import org.hamcrest.Matcher
import org.hamcrest.StringDescription

/**
 * Inspired by https://github.com/AzimoLabs/ConditionWatcher/issues/7
 */
class ViewMatchWaiter @JvmOverloads constructor(
    private val viewMatcher: Matcher<View>,
    private val policy: ConditionWatcher.Policy = ConditionWatcher.SHORT_WAIT_POLICY
) {

    fun toMatch(viewChecker: Matcher<View>) {
        ConditionWatcher.waitForCondition(object : ConditionWatcher.Condition {
            override fun testCondition(): Boolean {
                return try {
                    onView(viewMatcher).check(matches(viewChecker))
                    true
                } catch (ex: RuntimeException) {
                    false
                }
            }

            override fun getDescription(): String {
                val description = StringDescription()
                description.appendText("Wait for view ")
                viewMatcher.describeTo(description)
                description.appendText(" to match ")
                viewChecker.describeTo(description)
                return description.toString()
            }
        }, policy)
    }

    fun toCheck(viewAssertion: ViewAssertion) {
        ConditionWatcher.waitForCondition(object : ConditionWatcher.Condition {
            override fun testCondition(): Boolean {
                return try {
                    onView(viewMatcher).check(viewAssertion)
                    true
                } catch (ex: RuntimeException) {
                    false
                }
            }

            override fun getDescription(): String {
                val description = StringDescription()
                description.appendText("Wait for view ")
                viewMatcher.describeTo(description)
                description.appendText(" to match ")
                description.appendText(viewAssertion.toString())
                return description.toString()
            }
        }, policy)
    }

    companion object {
        fun waitForView(viewMatcher: Matcher<View>): ViewMatchWaiter = ViewMatchWaiter(viewMatcher)
    }
}
