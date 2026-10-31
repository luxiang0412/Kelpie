package dev.herdr.core.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

internal object InstrumentationProvider {
    fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext
}
