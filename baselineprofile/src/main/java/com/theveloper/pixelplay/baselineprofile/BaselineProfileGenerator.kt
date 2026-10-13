package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "io.github.aaryan1101.pixelplayerplus"
private const val PROFILE_ITERATIONS = 5
private const val UI_TIMEOUT_MS = 15_000L
private const val NAVIGATION_TIMEOUT_MS = 5_000L

/**
 * Generates a deliberately small and repeatable profile.
 *
 * Startup is kept separate from the common navigation journey so startup rules are not
 * polluted by optional screens or device-specific media. Playback, queue, and stats flows
 * belong in macrobenchmarks because they depend on a prepared media library.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        rule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
            maxIterations = PROFILE_ITERATIONS
        ) {
            grantRuntimePermissions()
            pressHome()
            startActivityAndWait()
            requireAppVisible()
        }
    }

    @Test
    fun generateCoreNavigationProfile() {
        rule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
            maxIterations = PROFILE_ITERATIONS
        ) {
            grantRuntimePermissions()
            pressHome()
            startActivityAndWait()
            requireAppVisible()
            dismissFirstRunPrompts()

            openNavigationTab("Library")
            exerciseVerticalList()

            openNavigationTab("Search")
            exerciseVerticalList()

            openNavigationTab("Home")
            exerciseVerticalList()
        }
    }

    private fun MacrobenchmarkScope.grantRuntimePermissions() {
        listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE"
        ).forEach { permission ->
            device.executeShellCommand("pm grant $PACKAGE_NAME $permission")
        }
    }

    private fun MacrobenchmarkScope.requireAppVisible() {
        check(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), UI_TIMEOUT_MS)) {
            "PixelPlayer did not become visible within $UI_TIMEOUT_MS ms"
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.dismissFirstRunPrompts() {
        val actionPattern = Pattern.compile(
            "^(Allow|Permitir|Continue|Next|Skip|Done|Siguiente|Omitir|Empezar)$",
            Pattern.CASE_INSENSITIVE
        )
        repeat(4) {
            val action = device.wait(Until.findObject(By.text(actionPattern)), 750L)
                ?: return
            action.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.openNavigationTab(label: String) {
        val exactLabel = Pattern.compile("^${Pattern.quote(label)}$", Pattern.CASE_INSENSITIVE)
        val tab = device.wait(Until.findObject(By.desc(exactLabel)), NAVIGATION_TIMEOUT_MS)
            ?: device.wait(Until.findObject(By.text(exactLabel)), NAVIGATION_TIMEOUT_MS)
        checkNotNull(tab) { "Navigation tab '$label' was not found" }
        tab.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.exerciseVerticalList() {
        val centerX = device.displayWidth / 2
        val upperY = (device.displayHeight * 0.28f).toInt()
        val lowerY = (device.displayHeight * 0.72f).toInt()

        device.swipe(centerX, lowerY, centerX, upperY, 30)
        device.waitForIdle()
        device.swipe(centerX, upperY, centerX, lowerY, 30)
        device.waitForIdle()
    }
}
