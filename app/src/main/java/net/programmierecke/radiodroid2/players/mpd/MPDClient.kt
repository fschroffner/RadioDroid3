package net.programmierecke.radiodroid2.players.mpd

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.arch.core.util.Function
import androidx.lifecycle.LiveData
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class MPDClient(context: Context) {
    private var userTaskThreadPool = Executors.newScheduledThreadPool(1)
    private var connectionCheckerThreadPool = Executors.newScheduledThreadPool(2)

    val mpdServersRepository = MPDServersRepository(context)
    private val mpdServers: LiveData<List<MPDServerData>> = mpdServersRepository.getAllServers()

    private val mainThreadHandler = Handler(context.mainLooper)
    private val serverChangesQueue = ConcurrentLinkedQueue<MPDServerData>()

    private val aliveMpdServers = HashSet<MPDServerData>()
    private val deadMpdServers = HashSet<MPDServerData>()
    private val serversLock = Any()

    private val quickMPDStatusChecker = QuickMPDStatusChecker()
    private var quickCheckFuture: Future<*>? = null
    private val quickFutureLock = Any()

    private val aliveMPDStatusChecker = AliveMPDStatusChecker()
    private var aliveCheckFuture: Future<*>? = null
    private val aliveFutureLock = Any()

    private val deadMPDStatusChecker = DeadMPDStatusChecker()
    private var deadCheckFuture: Future<*>? = null
    private val deadFutureLock = Any()

    var isMpdEnabled = false
        private set
    private var autoUpdateEnabled = false

    fun enqueueTask(server: MPDServerData, task: MPDAsyncTask) {
        if (!isMpdEnabled) {
            Log.e(TAG, "Trying to enqueue task when mpd is not enabled!")
            return
        }
        task.setTimeout(getTimeout(server.hostname).toLong())
        task.setParams(this, server)
        userTaskThreadPool.submit(task)
    }

    fun enableAutoUpdate() {
        if (!isMpdEnabled) {
            setMPDEnabled(true)
            Log.w(TAG, "enableAutoUpdate called with mpd disabled, enabling mpd")
        }
        autoUpdateEnabled = true
        mpdServersRepository.resetAllConnectionStatus()
        synchronized(quickFutureLock) {
            quickMPDStatusChecker.setServers(mpdServers.value?.toMutableList() ?: mutableListOf())
            quickCheckFuture = connectionCheckerThreadPool.submit(quickMPDStatusChecker)
        }
    }

    fun disableAutoUpdate() {
        autoUpdateEnabled = false
        cancelCheckFutures()
    }

    fun launchQuickCheck() {
        if (!autoUpdateEnabled) {
            Log.e(TAG, "Trying to launch quick servers check while autoUpdateEnabled = false!")
            return
        }
        cancelCheckFutures()
        synchronized(quickFutureLock) {
            quickMPDStatusChecker.setServers(mpdServers.value?.toMutableList() ?: mutableListOf())
            quickCheckFuture = connectionCheckerThreadPool.submit(quickMPDStatusChecker)
        }
    }

    private fun cancelCheckFutures() {
        synchronized(quickFutureLock) { quickCheckFuture?.cancel(true); quickCheckFuture = null }
        synchronized(aliveFutureLock) { aliveCheckFuture?.cancel(true); aliveCheckFuture = null }
        synchronized(deadFutureLock) { deadCheckFuture?.cancel(true); deadCheckFuture = null }
        aliveMpdServers.clear()
        deadMpdServers.clear()
    }

    fun setMPDEnabled(enabled: Boolean) {
        if (enabled != isMpdEnabled) {
            if (enabled) enableThreadPools() else disableAutoUpdate()
            isMpdEnabled = enabled
        }
    }

    fun notifyServerUpdate(mpdServerData: MPDServerData) {
        serverChangesQueue.add(mpdServerData)
        mainThreadHandler.post {
            var changedData: MPDServerData?
            while (serverChangesQueue.poll().also { changedData = it } != null) {
                mpdServersRepository.updateRuntimeData(changedData!!)
            }
        }
    }

    private fun enableThreadPools() {
        if (userTaskThreadPool.isShutdown) userTaskThreadPool = Executors.newScheduledThreadPool(1)
        if (connectionCheckerThreadPool.isShutdown) connectionCheckerThreadPool = Executors.newScheduledThreadPool(2)
    }

    private fun checkServers(servers: Iterable<MPDServerData>, timeoutFunc: Function<MPDServerData, Int>) {
        for (mpdServerData in servers) {
            val task = MPDAsyncTask()
            task.setStages(
                arrayOf(MPDAsyncTask.okReadStage(), MPDAsyncTask.statusReadStage(false)),
                arrayOf(MPDAsyncTask.statusWriteStage()),
                MPDAsyncTask.FailureCallback { t ->
                    if (t.getMpdServerData().connected) {
                        t.getMpdServerData().connected = false
                        t.notifyServerUpdated()
                    }
                }
            )
            task.setTimeout(timeoutFunc.apply(mpdServerData).toLong())
            task.setParams(this, mpdServerData)
            task.run()

            synchronized(serversLock) {
                if (mpdServerData.connected) {
                    aliveMpdServers.add(mpdServerData)
                    deadMpdServers.remove(mpdServerData)
                } else {
                    aliveMpdServers.remove(mpdServerData)
                    deadMpdServers.add(mpdServerData)
                }
            }
        }
    }

    private inner class QuickMPDStatusChecker : Runnable {
        private var servers: List<MPDServerData> = emptyList()
        fun setServers(servers: List<MPDServerData>) { this.servers = servers }

        override fun run() {
            checkServers(servers) { QUICK_REFRESH_TIMEOUT }
            synchronized(aliveFutureLock) {
                aliveCheckFuture = connectionCheckerThreadPool.schedule(aliveMPDStatusChecker, 2, TimeUnit.SECONDS)
            }
            synchronized(deadFutureLock) {
                deadCheckFuture = connectionCheckerThreadPool.schedule(deadMPDStatusChecker, 0, TimeUnit.SECONDS)
            }
        }
    }

    private inner class AliveMPDStatusChecker : Runnable {
        override fun run() {
            val aliveServers = synchronized(serversLock) { ArrayList(aliveMpdServers) }
            checkServers(aliveServers) { ALIVE_REFRESH_TIMEOUT }
            if (autoUpdateEnabled) {
                synchronized(aliveFutureLock) {
                    aliveCheckFuture = connectionCheckerThreadPool.schedule(this, 2, TimeUnit.SECONDS)
                }
            }
        }
    }

    private inner class DeadMPDStatusChecker : Runnable {
        override fun run() {
            val deadServers = synchronized(serversLock) { ArrayList(deadMpdServers) }
            checkServers(deadServers) { DEAD_REFRESH_TIMEOUT }
            if (autoUpdateEnabled) {
                synchronized(deadFutureLock) {
                    deadCheckFuture = connectionCheckerThreadPool.schedule(this, 8, TimeUnit.SECONDS)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MPDClient"
        private const val QUICK_REFRESH_TIMEOUT = 150
        private const val ALIVE_REFRESH_TIMEOUT = 1000
        private const val DEAD_REFRESH_TIMEOUT = 1000

        private fun getTimeout(hostname: String): Int =
            if (hostname.startsWith("192.168.") || hostname.startsWith("127.0.") ||
                hostname.startsWith("localhost") || hostname.startsWith("10.") ||
                hostname.contains(".local")) 300 else 2000
    }
}
