package com.github.fschroffner.radiodroid3.tests

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.viewpager.widget.ViewPager
import com.yariksoffice.lingver.Lingver
import com.github.fschroffner.radiodroid3.ActivityMain
import com.github.fschroffner.radiodroid3.BuildConfig
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.tests.utils.RecyclerViewMatcher.Companion.withRecyclerView
import com.github.fschroffner.radiodroid3.tests.utils.TestUtils
import com.github.fschroffner.radiodroid3.tests.utils.ViewPagerIdlingResource
import org.hamcrest.core.AllOf
import org.hamcrest.core.Is
import org.hamcrest.core.StringStartsWith.startsWith
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

@LargeTest
@RunWith(Parameterized::class)
class UILangTest {

    @JvmField
    @Parameterized.Parameter(0)
    var locale: Locale = Locale.ENGLISH

    @get:Rule
    val activityRule = object : ActivityTestRule<ActivityMain>(ActivityMain::class.java) {
        override fun beforeActivityLaunched() {
            Lingver.init(ApplicationProvider.getApplicationContext<Application>(), locale)
            super.beforeActivityLaunched()
        }
    }

    private fun getCurrentLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ApplicationProvider.getApplicationContext<Context>()
                .resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            ApplicationProvider.getApplicationContext<Context>()
                .resources.configuration.locale
        }
    }

    @Before
    fun setUp() {
        try {
            assertThat("Locale is not supported", getCurrentLocale(), Is.`is`(locale))

            TestUtils.populateFavourites(ApplicationProvider.getApplicationContext(), STATIONS_COUNT)
            TestUtils.populateHistory(ApplicationProvider.getApplicationContext(), STATIONS_COUNT)
        } catch (e: AssertionError) {
            assertThat(e.message, startsWith("Locale is not supported"))
        }
    }

    @Test
    fun application_ShouldNotCrash_WithLanguage() {
        val idlingResource = ViewPagerIdlingResource(activityRule.activity.findViewById<ViewPager>(R.id.viewpager), "ViewPager")
        IdlingRegistry.getInstance().register(idlingResource)

        onView(AllOf.allOf(withId(R.id.buttonMore), isDescendantOfA(withRecyclerView(R.id.recyclerViewStations).atPosition(0)))).perform(ViewActions.click())

        onView(withId(R.id.nav_item_starred)).perform(ViewActions.click())

        onView(withId(R.id.nav_item_history)).perform(ViewActions.click())

        onView(withId(R.id.nav_item_alarm)).perform(ViewActions.click())

        onView(withId(R.id.nav_item_settings)).perform(ViewActions.click())

        onView(withId(R.id.fragment_player_small)).perform(ViewActions.swipeUp())
    }

    companion object {
        private const val STATIONS_COUNT = 20

        @JvmStatic
        @Parameterized.Parameters(name = "locale={0}")
        fun initParameters(): CopyOnWriteArrayList<Array<Any>> {
            val params = CopyOnWriteArrayList<Array<Any>>()
            for (availableLocale in BuildConfig.AVAILABLE_LOCALES) {
                params.add(arrayOf<Any>(parseLocale(availableLocale)))
            }
            return params
        }

        private fun parseLocale(str: String): Locale {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return Locale.forLanguageTag(str)
            } else {
                if (str.contains("-")) {
                    val args = str.split("-")
                    if (args.size > 2) {
                        return Locale(args[0], args[1], args[3])
                    } else if (args.size > 1) {
                        return Locale(args[0], args[1])
                    } else if (args.size == 1) {
                        return Locale(args[0])
                    }
                }

                return Locale(str)
            }
        }
    }
}
