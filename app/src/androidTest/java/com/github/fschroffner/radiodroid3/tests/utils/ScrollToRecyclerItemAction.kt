package com.github.fschroffner.radiodroid3.tests.utils

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import org.hamcrest.Matcher

class ScrollToRecyclerItemAction(private val itemIdx: Int) : ViewAction {

    override fun getConstraints(): Matcher<View> = isDisplayingAtLeast(VIEW_DISPLAY_PERCENTAGE)

    override fun getDescription(): String = String.format("scroll to item %d in recycler", itemIdx)

    override fun perform(uiController: UiController, view: View) {
        val recyclerView = view as RecyclerView
        TestUtils.centerItemInRecycler(uiController, recyclerView, itemIdx)
    }

    companion object {
        private const val VIEW_DISPLAY_PERCENTAGE = 50

        fun scrollToRecyclerItem(itemIdx: Int): ScrollToRecyclerItemAction = ScrollToRecyclerItemAction(itemIdx)
    }
}
