package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.pdf.PdfCompressorEngine
import com.example.data.scan.DocumentOcrEngine
import com.example.ui.components.QrCodeGenerator
import com.example.ui.components.decodeQrBitmap
import com.example.update.UpdateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDF (AIO)", appName)
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

  @Test
  fun `qr generator and decoder round trip works and blank image returns null`() {
    val expected = "https://github.com/ishantkohli/PDF-AIO"
    val qrBmp = QrCodeGenerator.generateQrBitmap(expected, width = 370, height = 370)
    try {
      val decoded = decodeQrBitmap(qrBmp)
      assertEquals(expected, decoded)
    } finally {
      qrBmp.recycle()
    }

    val blankBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.WHITE)
    }
    try {
      assertNull(decodeQrBitmap(blankBmp))
    } finally {
      blankBmp.recycle()
    }
  }

  @Test
  fun `document ocr engine reports honest metrics without fabricated templates`() = runBlocking {
    val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.WHITE)
    }
    try {
      val res = DocumentOcrEngine.recognizeTextFromBitmap(bmp, pageIndex = 0)
      assertTrue(res.fullText.isNotBlank())
      assertFalse(res.fullText.contains("CONFIDENTIAL NON-DISCLOSURE"))
      assertFalse(res.fullText.contains("ACME CORP"))
      assertFalse(res.fullText.contains("Adobe"))
    } finally {
      bmp.recycle()
    }
  }

  @Test
  fun `update manager normalizes legacy owner and uses PDF-AIO repo`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    UpdateManager.setGithubRepo(context, "ishantkohlikohli", "PDF-AIO")
    assertEquals("ishantkohli", UpdateManager.getGithubOwner(context))
    assertEquals("PDF-AIO", UpdateManager.getGithubRepo(context))
  }

  @Test
  fun `pdf compressor returns clear failure when file is missing`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val missing = File(context.cacheDir, "does_not_exist_${System.currentTimeMillis()}.pdf")
    val result = PdfCompressorEngine.compressPdf(context, missing)
    assertFalse(result.isSuccess)
    assertNull(result.outputFile)
    assertTrue(result.errorMessage?.isNotBlank() == true)
  }

  @Test
  fun `all home screen widgets expose valid preview layouts and preview images`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = android.content.Intent("android.appwidget.action.APPWIDGET_UPDATE").apply {
      setPackage(context.packageName)
    }
    val resolvedReceivers = context.packageManager.queryBroadcastReceivers(
      intent,
      android.content.pm.PackageManager.GET_META_DATA
    )
    assertEquals(7, resolvedReceivers.size)

    val ns = "http://schemas.android.com/apk/res/android"
    for (ri in resolvedReceivers) {
      val meta = ri.activityInfo.metaData
      val providerXmlId = meta?.getInt("android.appwidget.provider") ?: 0
      assertTrue("Missing provider xml metadata for ${ri.activityInfo.name}", providerXmlId != 0)

      val parser = context.resources.getXml(providerXmlId)
      var foundProviderTag = false
      while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
          foundProviderTag = true
          val initialLayoutId = parser.getAttributeResourceValue(ns, "initialLayout", 0)
          val previewLayoutId = parser.getAttributeResourceValue(ns, "previewLayout", 0)
          val previewImageId = parser.getAttributeResourceValue(ns, "previewImage", 0)
          val descId = parser.getAttributeResourceValue(ns, "description", 0)

          assertTrue("Missing initialLayout for ${ri.activityInfo.name}", initialLayoutId != 0)
          assertTrue("Missing previewLayout for ${ri.activityInfo.name}", previewLayoutId != 0)
          assertTrue("Missing previewImage for ${ri.activityInfo.name}", previewImageId != 0)
          assertTrue("Missing description for ${ri.activityInfo.name}", descId != 0)
          assertTrue(context.getString(descId).isNotBlank())
        }
      }
      assertTrue("Missing <appwidget-provider> root tag for ${ri.activityInfo.name}", foundProviderTag)
    }
  }
}
