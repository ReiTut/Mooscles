package com.mooscles

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LauncherIconHostTest {
    private val resources get() = ApplicationProvider.getApplicationContext<Application>().resources

    @Test fun adaptiveIconHasBackgroundForegroundAndMonochrome() {
        listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round).forEach { id ->
            val icon = ResourcesCompat.getDrawable(resources, id, null) as AdaptiveIconDrawable
            assertNotNull(icon.background)
            assertNotNull(icon.foreground)
            assertNotNull(icon.monochrome)
        }
    }
}
