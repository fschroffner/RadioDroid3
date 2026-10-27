package com.github.fschroffner.radiodroid3.tests.utils

import androidx.test.espresso.IdlingResource
import androidx.viewpager2.widget.ViewPager2

class ViewPagerIdlingResource(viewPager: ViewPager2, name: String) : IdlingResource {
    private val resourceName: String = name

    private var isIdle = true

    private var resourceCallback: IdlingResource.ResourceCallback? = null

    init {
        viewPager.registerOnPageChangeCallback(ViewPager2Listener())
    }

    override fun getName(): String = resourceName

    override fun isIdleNow(): Boolean = isIdle

    override fun registerIdleTransitionCallback(resourceCallback: IdlingResource.ResourceCallback) {
        this.resourceCallback = resourceCallback
    }

    private inner class ViewPager2Listener : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrollStateChanged(state: Int) {
            isIdle = state == ViewPager2.SCROLL_STATE_IDLE
            if (isIdle) {
                resourceCallback?.onTransitionToIdle()
            }
        }
    }
}
