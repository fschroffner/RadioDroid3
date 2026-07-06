package com.github.fschroffner.radiodroid3.tests.utils

import androidx.test.espresso.IdlingResource
import androidx.viewpager.widget.ViewPager

class ViewPagerIdlingResource(viewPager: ViewPager, name: String) : IdlingResource {
    private val resourceName: String = name

    private var isIdle = true

    private var resourceCallback: IdlingResource.ResourceCallback? = null

    init {
        viewPager.addOnPageChangeListener(ViewPagerListener())
    }

    override fun getName(): String = resourceName

    override fun isIdleNow(): Boolean = isIdle

    override fun registerIdleTransitionCallback(resourceCallback: IdlingResource.ResourceCallback) {
        this.resourceCallback = resourceCallback
    }

    private inner class ViewPagerListener : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageScrollStateChanged(state: Int) {
            isIdle = state == ViewPager.SCROLL_STATE_IDLE
            if (isIdle) {
                resourceCallback?.onTransitionToIdle()
            }
        }
    }
}
