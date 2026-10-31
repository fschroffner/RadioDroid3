package com.github.fschroffner.radiodroid3.data.loading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modern reactive state holder for app-wide and UI loading indicators.
 * Replaces deprecated LocalBroadcastManager (ACTION_SHOW_LOADING / ACTION_HIDE_LOADING)
 * with a StateFlow that lifecycle-aware UI components can safely collect.
 */
@Singleton
class LoadingStateHolder @Inject constructor() {

    private val activeLoads = AtomicInteger(0)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        instance = this
    }

    fun showLoading() {
        if (activeLoads.incrementAndGet() > 0) {
            _isLoading.value = true
        }
    }

    fun hideLoading() {
        val current = activeLoads.decrementAndGet()
        if (current <= 0) {
            activeLoads.set(0)
            _isLoading.value = false
        }
    }

    fun reset() {
        activeLoads.set(0)
        _isLoading.value = false
    }

    companion object {
        @Volatile
        private var instance: LoadingStateHolder? = null

        @JvmStatic
        fun get(): LoadingStateHolder = instance ?: synchronized(this) {
            instance ?: LoadingStateHolder().also { instance = it }
        }

        @JvmStatic
        fun show() {
            get().showLoading()
        }

        @JvmStatic
        fun hide() {
            get().hideLoading()
        }
    }
}
