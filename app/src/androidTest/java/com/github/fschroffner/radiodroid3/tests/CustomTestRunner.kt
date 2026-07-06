package com.github.fschroffner.radiodroid3.tests

import android.app.Application
import android.content.Intent
import android.os.Build
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
import java.net.InetAddress

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
        // Must run before super.onStart(), which is where the tests are actually
        // executed. Granting the notification permission here prevents the runtime
        // permission dialog from pausing the activity during the tests.
        grantRuntimePermissions()

        super.onStart()

        clearUnintendedDialogs()
    }

    private fun grantRuntimePermissions() {
        // ActivityMain requests POST_NOTIFICATIONS at runtime on Android 13+. The
        // resulting system dialog pauses the activity, so Espresso interactions fail
        // with NoActivityResumedException. Pre-grant it so no dialog is shown.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uiAutomation.grantRuntimePermission(
                targetContext.packageName,
                "android.permission.POST_NOTIFICATIONS"
            )
        }
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
            // Bind to the numeric loopback address instead of relying on
            // MockWebServer's default, which resolves "localhost" via DNS.
            // Some emulator images fail that lookup (EAI_NODATA), crashing the
            // whole instrumentation process before any test runs.
            mockWebServer.start(InetAddress.getByName("127.0.0.1"), 0)
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
