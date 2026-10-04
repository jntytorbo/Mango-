package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
object PlayerFactory {

    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun createPlayer(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                playWhenReady = true
            }
    }

    fun openInExternalPlayer(context: Context, url: String, title: String? = null, headers: Map<String, String> = emptyMap()) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "No stream URL available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(trimmed), "video/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

                if (!title.isNullOrBlank()) {
                    putExtra("title", title)
                    putExtra(Intent.EXTRA_TITLE, title)
                }

                if (headers.isNotEmpty()) {
                    val bundle = Bundle()
                    headers.forEach { (k, v) -> bundle.putString(k, v) }
                    putExtra("headers", bundle)
                    putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Play with external player..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open external player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
