package com.theveloper.pixelplay.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compares uncompiled startup with startup using the generated baseline profile. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() = startup(
        // Some OEM builds cannot expose profile state reliably, so use it when available.
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable
        )
    )

    @Test
    fun coreNavigationFrameTiming() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.UseIfAvailable
        ),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        openNavigationTab("Library")
        swipeListDownAndUp()
        openNavigationTab("Search")
        swipeListDownAndUp()
        openNavigationTab("Home")
        swipeListDownAndUp()
    }

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }

    private fun MacrobenchmarkScope.openNavigationTab(label: String) {
        val exactLabel = Pattern.compile("^${Pattern.quote(label)}$", Pattern.CASE_INSENSITIVE)
        val tab = device.wait(Until.findObject(By.desc(exactLabel)), NAVIGATION_TIMEOUT_MS)
            ?: device.wait(Until.findObject(By.text(exactLabel)), NAVIGATION_TIMEOUT_MS)
        checkNotNull(tab) { "Navigation tab '$label' was not found" }
        tab.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.swipeListDownAndUp() {
        val centerX = device.displayWidth / 2
        val upperY = (device.displayHeight * 0.28f).toInt()
        val lowerY = (device.displayHeight * 0.72f).toInt()

        device.swipe(centerX, lowerY, centerX, upperY, 30)
        device.waitForIdle()
        device.swipe(centerX, upperY, centerX, lowerY, 30)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "io.github.aaryan1101.pixelplayerplus"
        const val ITERATIONS = 5
        const val NAVIGATION_TIMEOUT_MS = 5_000L
    }
}
