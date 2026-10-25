package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream

/**
 * Prominent Share App Card displaying an on-device generated QR code and native share actions.
 * Featured on Home Screen and Developer Tab.
 */
@Composable
fun ShareAppCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shareUrl = "https://github.com/ishantkohli/PDF-AIO"

    val qrBitmap = remember {
        QrCodeGenerator.generateQrBitmap(
            content = shareUrl,
            width = 400,
            height = 400,
            foregroundColor = android.graphics.Color.BLACK,
            backgroundColor = android.graphics.Color.WHITE
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AdfObsidianSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(AdfElectricCyan.copy(alpha = 0.6f), Color(0xFFD946EF).copy(alpha = 0.6f))
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("share_app_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AdfElectricCyan.copy(alpha = 0.2f), Color(0xFFD946EF).copy(alpha = 0.2f))
                            )
                        )
                        .border(1.dp, AdfElectricCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = AdfElectricCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Share PDF AIO",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFD946EF).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "BY ISHANT",
                                color = Color(0xFFD946EF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }

                    Text(
                        text = "Scan QR code or share link with friends",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = AdfTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // QR Code & Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.5.dp, AdfElectricCyan, RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "PDF AIO Download QR Code (Black & White)",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "100% Free & Offline",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AdfElectricCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Share PDF AIO with friends, students, & colleagues for fast document viewing, scanning, and signatures.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = AdfTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        saveQrBitmapToDevice(context, qrBitmap)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("save_qr_code_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = AdfElectricCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save QR", color = AdfElectricCyan, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        shareAppLink(context, shareUrl)
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(44.dp)
                        .testTag("share_app_link_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share App Link", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun shareAppLink(context: Context, url: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out PDF AIO by Ishant — Next-Gen 100% Offline PDF Reader, Scanner & Editor! Download free at: $url"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share PDF AIO")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share link: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun saveQrBitmapToDevice(context: Context, bitmap: Bitmap) {
    try {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "PDF_AIO").apply { mkdirs() }
        val qrFile = File(appDir, "pdf_aio_qr_code.png")

        FileOutputStream(qrFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        Toast.makeText(context, "QR Code saved to Pictures/PDF_AIO!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Saved to cache: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
