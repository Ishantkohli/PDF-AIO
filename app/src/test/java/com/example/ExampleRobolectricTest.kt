package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDF AIO", appName)
  }

  @Test
  fun `verify typed signature json serialization`() {
    val typedJson = org.json.JSONObject().apply {
      put("type", "TYPED")
      put("text", "Ishant Kohli")
      put("style", "CURSIVE_FLOW")
      put("colorHex", -14862454L)
    }.toString()

    val parsed = org.json.JSONObject(typedJson)
    assertEquals("TYPED", parsed.getString("type"))
    assertEquals("Ishant Kohli", parsed.getString("text"))
    assertEquals("CURSIVE_FLOW", parsed.getString("style"))
  }
}
