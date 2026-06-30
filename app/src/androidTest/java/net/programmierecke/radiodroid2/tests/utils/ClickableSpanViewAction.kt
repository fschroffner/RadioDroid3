package net.programmierecke.radiodroid2.tests.utils

import android.text.Spannable
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.Matcher
import org.hamcrest.Matchers.instanceOf

class ClickableSpanViewAction : ViewAction {
    private var spanIndex = -1
    private var textToClick: CharSequence? = null

    constructor(spanIndex: Int) {
        this.spanIndex = spanIndex
    }

    constructor(textToClick: CharSequence) {
        this.textToClick = textToClick
    }

    override fun getConstraints(): Matcher<View> = instanceOf(TextView::class.java)

    override fun getDescription(): String = "clicking on a ClickableSpan"

    override fun perform(uiController: UiController, view: View) {
        val textView = view as TextView
        val spannableString = textView.text as Spannable
        if (spannableString.isEmpty()) {
            throw NoMatchingViewException.Builder()
                .includeViewHierarchy(true)
                .withRootView(textView)
                .build()
        }

        val spans = spannableString.getSpans(0, spannableString.length, ClickableSpan::class.java)

        if (spans.isNotEmpty()) {
            if (spanIndex >= spans.size) {
                throw NoMatchingViewException.Builder()
                    .includeViewHierarchy(true)
                    .withRootView(textView)
                    .build()
            } else if (spanIndex >= 0) {
                spans[spanIndex].onClick(textView)
                return
            }

            for (span in spans) {
                val start = spannableString.getSpanStart(span)
                val end = spannableString.getSpanEnd(span)
                val sequence = spannableString.subSequence(start, end)
                if (textToClick.toString() == sequence.toString()) {
                    span.onClick(textView)
                    return
                }
            }
        }

        throw NoMatchingViewException.Builder()
            .includeViewHierarchy(true)
            .withRootView(textView)
            .build()
    }

    companion object {
        fun clickClickableSpan(textToClick: CharSequence): ViewAction = ClickableSpanViewAction(textToClick)

        fun clickClickableSpan(spanIndex: Int): ViewAction = ClickableSpanViewAction(spanIndex)
    }
}
