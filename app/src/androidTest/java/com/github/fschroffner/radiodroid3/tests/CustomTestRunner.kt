package com.github.fschroffner.radiodroid3.tests

import android.app.Application
import android.content.Intent
import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import com.github.fschroffner.radiodroid3.RadioDroidApp
import com.github.fschroffner.radiodroid3.tests.utils.http.HttpToMockInterceptor
import com.github.fschroffner.radiodroid3.tests.utils.http.MockHttpDispatcher
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException

class CustomTestRunner : AndroidJUnitRunner() {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpToMockInterceptor: HttpToMockInterceptor
    private lateinit var mockHttpDispatcher: MockHttpDispatcher

    override fun callApplicationOnCreate(app: Application) {
        resetGlobalState(app)
        setupMockWebServer()

        val radioDroidApp = app as RadioDroidApp
        radioDroidApp.testsInterceptor = httpToMockInterceptor

        super.callApplicationOnCreate(app)
    }

    override fun onStart() {
        super.onStart()

        clearUnintendedDialogs()
    }

    fun setCustomRequestDispatcher(customRequestDispatcher: MockHttpDispatcher.CustomRequestDispatcher?) {
        mockHttpDispatcher.setCustomRequestDispatcher(customRequestDispatcher)
    }

    private fun resetGlobalState(app: Application) {
        // We may have opened notifications
        val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        app.sendBroadcast(closeIntent)
    }

    private fun setupMockWebServer() {
        // Changing ThreadPolicy to circumvent NetworkOnMainThreadException is BAD.
        // However we want to initialize local web server here, without additional mating dances.
        val newPolicy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        val oldPolicy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(newPolicy)

        mockHttpDispatcher = MockHttpDispatcher()

        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = mockHttpDispatcher

        try {
            mockWebServer.start()
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }

        httpToMockInterceptor = HttpToMockInterceptor(mockWebServer)

        StrictMode.setThreadPolicy(oldPolicy)
    }

    private fun clearUnintendedDialogs() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val commonButtons = arrayOf("Cancel", "Dismiss", "No", "OK", "Yes")

        var button: UiObject? = null
        for (keyword in commonButtons) {
            button = uiDevice.findObject(UiSelector().text(keyword).enabled(true))
            if (button != null && button.exists()) {
                break
            }
        }

        try {
            if (button != null && button.exists()) {
                button.waitForExists(1000)
                button.click()
            }
        } catch (ignored: UiObjectNotFoundException) {
        }
    }
}
