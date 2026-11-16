package com.thoughtdrop.app.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thoughtdrop.app.ui.theme.ObsidianBorder
import com.thoughtdrop.app.ui.theme.ObsidianSurfaceElevated
import com.thoughtdrop.app.ui.theme.TextMuted
import java.io.File
import java.io.FileOutputStream

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object ImageUtils {

    fun createTempImageFile(context: Context): File {
        val dir = File(context.filesDir, "note_images").apply { mkdirs() }
        return File(dir, "img_${System.currentTimeMillis()}.jpg")
    }

    fun getImageUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun copyUriToFile(context: Context, uri: Uri): File? {
        return try {
            val destination = createTempImageFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            destination
        } catch (_: Exception) {
            null
        }
    }

    fun loadScaledBitmap(filePath: String, maxDim: Int = 1000): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)

        var sampleSize = 1
        while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        return BitmapFactory.decodeFile(filePath, decodeOptions)
    }
}

@Composable
fun NoteImageCard(
    imagePath: String,
    modifier: Modifier = Modifier,
    maxHeight: Int = 220,
    onClick: (() -> Unit)? = null
) {
    val bitmap = remember(imagePath) {
        ImageUtils.loadScaledBitmap(imagePath, 1200)
    }

    if (bitmap != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Note photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight.dp)
            )
        }
    }
}

@Composable
fun FullScreenImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        val bitmap = remember(imagePath) {
            ImageUtils.loadScaledBitmap(imagePath, 2048)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF5090D0B))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4.5f)
                        offset = if (scale > 1f) {
                            Offset(offset.x + pan.x, offset.y + pan.y)
                        } else {
                            Offset.Zero
                        }
                    }
                }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full Review Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // Top Bar with Close button & Delete button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0x80000000), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0x80EF4444), CircleShape)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Photo", tint = Color.White)
                    }
                }
            }
        }
    }
}
