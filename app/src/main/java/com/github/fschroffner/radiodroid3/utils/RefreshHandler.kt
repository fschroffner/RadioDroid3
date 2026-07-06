package com.github.fschroffner.radiodroid3.utils

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/** Periodic refreshes that allow the target object to be garbage collected. */
class RefreshHandler {
    private val handler = Handler(Looper.getMainLooper())
    private var runnableDecorator: RunnableDecorator? = null

    fun executePeriodically(task: ObjectBoundRunnable<*>, interval: Long) {
        runnableDecorator?.let { handler.removeCallbacks(it) }
        runnableDecorator = RunnableDecorator(task, interval)
        handler.post(runnableDecorator!!)
    }

    fun cancel() {
        runnableDecorator?.let { handler.removeCallbacks(it) }
        runnableDecorator = null
    }

    private inner class RunnableDecorator(
        private val runnable: ObjectBoundRunnable<*>,
        private val interval: Long
    ) : Runnable {
        override fun run() {
            runnable.run()
            if (runnable.objectRef.get() != null && !runnable.terminate) {
                handler.postDelayed(this, interval)
            } else {
                handler.removeCallbacks(this)
                runnableDecorator = null
            }
        }
    }

    abstract class ObjectBoundRunnable<T>(obj: T) : Runnable {
        val objectRef = WeakReference(obj)
        var terminate = false
            private set

        override fun run() { objectRef.get()?.let { run(it) } }

        protected fun terminate() { terminate = true }

        protected abstract fun run(obj: T)
    }
}
