package dev.owohunt.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port 1:1 dari target.test.ts web. */
class TargetParseTest {

    @Test
    fun `true untuk mCurrentFocus Discord`() {
        assertTrue(
            isDiscordForeground(
                "mCurrentFocus=Window{abc123 u0 com.discord/com.discord.main.MainActivity}",
            ),
        )
    }

    @Test
    fun `false untuk app lain`() {
        assertFalse(
            isDiscordForeground(
                "mCurrentFocus=Window{abc u0 com.android.settings/.Settings}",
            ),
        )
    }

    @Test
    fun `fallback longgar ada com discord di output`() {
        assertTrue(
            isDiscordForeground("mFocusedApp=ActivityRecord{... com.discord/.main x}"),
        )
    }

    private val windowsDump = """
      Window #17 Window{de514a7 u0 com.discord/com.discord.main.MainDefault}:
        mBaseLayer=21000
        isOnScreen=true
        mSurfaceController=Surface(name=com.discord/com.discord.main.MainDefault)
      Window #18 Window{6022a7b u0 com.example.other/.Main}:
        isOnScreen=true
    """.trimIndent()

    @Test
    fun `true saat jendela Discord isOnScreen split-screen`() {
        assertTrue(isDiscordVisible(windowsDump))
    }

    @Test
    fun `false saat Discord ada tapi tidak di layar`() {
        assertFalse(
            isDiscordVisible(
                "Window #17 Window{x u0 com.discord/.main.Main}:\n    isOnScreen=false",
            ),
        )
    }

    @Test
    fun `false saat Discord sama sekali tidak ada`() {
        assertFalse(isDiscordVisible("Window #1 Window{x u0 com.other/.Main}:"))
    }

    private val xml = """
    <hierarchy rotation="0">
      <node index="0" text="" resource-id="com.discord:id/chat_input_wrapper" class="android.widget.FrameLayout" focusable="false" />
      <node index="1" text="" resource-id="com.discord:id/message_input" class="android.widget.EditText" focusable="true" />
      <node index="2" text="" resource-id="com.discord:id/send" class="android.widget.Button" focusable="true" />
    </hierarchy>
    """.trimIndent()

    @Test
    fun `temukan EditText composer`() {
        assertEquals("com.discord:id/message_input", parseComposerId(xml))
    }

    @Test
    fun `null kalau tidak ada kandidat`() {
        assertNull(
            parseComposerId(
                "<hierarchy><node resource-id=\"other:id/x\" class=\"android.widget.TextView\"/></hierarchy>",
            ),
        )
    }
}
