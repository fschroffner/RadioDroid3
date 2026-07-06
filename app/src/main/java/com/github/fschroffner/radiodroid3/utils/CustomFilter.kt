package com.github.fschroffner.radiodroid3.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log

abstract class CustomFilter {
    private var mThreadHandler: Handler? = null
    private val mResultHandler: Handler = ResultsHandler()
    private var mDelayer: Delayer? = null
    private val mLock = Any()

    fun setDelayer(delayer: Delayer) { synchronized(mLock) { mDelayer = delayer } }

    fun filter(constraint: CharSequence?) = filter(constraint, null)

    fun filter(constraint: CharSequence?, listener: FilterListener?) {
        synchronized(mLock) {
            if (mThreadHandler == null) {
                val thread = HandlerThread(THREAD_NAME, android.os.Process.THREAD_PRIORITY_BACKGROUND)
                thread.start()
                mThreadHandler = RequestHandler(thread.looper)
            }
            val delay = mDelayer?.getPostingDelay(constraint) ?: 0L
            val message = mThreadHandler!!.obtainMessage(FILTER_TOKEN)
            message.obj = RequestArguments().apply {
                this.constraint = constraint?.toString()
                this.listener = listener
            }
            mThreadHandler!!.removeMessages(FILTER_TOKEN)
            mThreadHandler!!.removeMessages(FINISH_TOKEN)
            mThreadHandler!!.sendMessageDelayed(message, delay)
        }
    }

    protected abstract fun performFiltering(constraint: CharSequence?): FilterResults
    protected abstract fun publishResults(constraint: CharSequence?, results: FilterResults?)

    open fun convertResultToString(resultValue: Any?): CharSequence = resultValue?.toString() ?: ""

    protected class FilterResults {
        var values: Any? = null
        var count: Int = 0
    }

    interface FilterListener { fun onFilterComplete(count: Int) }

    private inner class RequestHandler(looper: android.os.Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                FILTER_TOKEN -> {
                    val args = msg.obj as RequestArguments
                    try {
                        args.results = performFiltering(args.constraint)
                    } catch (e: Exception) {
                        args.results = FilterResults()
                        Log.w(LOG_TAG, "An exception occured during performFiltering()!", e)
                    } finally {
                        mResultHandler.obtainMessage(msg.what).also { it.obj = args; it.sendToTarget() }
                    }
                    synchronized(mLock) {
                        mThreadHandler?.sendMessageDelayed(mThreadHandler!!.obtainMessage(FINISH_TOKEN), 3000)
                    }
                }
                FINISH_TOKEN -> synchronized(mLock) {
                    mThreadHandler?.looper?.quit()
                    mThreadHandler = null
                }
            }
        }
    }

    private inner class ResultsHandler : Handler() {
        override fun handleMessage(msg: Message) {
            val args = msg.obj as RequestArguments
            publishResults(args.constraint, args.results)
            args.listener?.onFilterComplete(args.results?.count ?: -1)
        }
    }

    private class RequestArguments {
        var constraint: CharSequence? = null
        var listener: FilterListener? = null
        var results: FilterResults? = null
    }

    interface Delayer { fun getPostingDelay(constraint: CharSequence?): Long }

    companion object {
        private const val LOG_TAG = "CustomFilter"
        private const val THREAD_NAME = "CustomFilter"
        private const val FILTER_TOKEN = 0xD0D0F00D.toInt()
        private const val FINISH_TOKEN = 0xDEADBEEF.toInt()
    }
}
