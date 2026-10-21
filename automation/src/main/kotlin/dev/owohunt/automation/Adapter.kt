package dev.owohunt.automation

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

interface AutomationAdapter {
    val name: String
    fun isAvailable(): Boolean
    fun checkForeground(): Boolean
    fun checkVisible(): Boolean
    fun sendCommand(text: String): Boolean
    fun clearComposer(): Boolean
    fun dumpUi(): String?
    fun keepScreenAwake(): Boolean
    fun restoreScreenTimeout(): Boolean
    fun dispose()
}

class MockAdapter : AutomationAdapter {
    override val name = "mock"
    override fun isAvailable() = true
    override fun checkForeground() = true
    override fun checkVisible() = true

    override fun sendCommand(text: String): Boolean = runBlocking {
        delay(20)
        true
    }

    override fun clearComposer() = true
    override fun dumpUi(): String? = null
    override fun keepScreenAwake() = true
    override fun restoreScreenTimeout() = true
    override fun dispose() {}
}
