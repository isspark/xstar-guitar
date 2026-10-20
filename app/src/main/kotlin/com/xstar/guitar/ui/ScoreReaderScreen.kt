package com.xstar.guitar.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.IOException

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScoreReaderScreen(songId: String, title: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }
    var capo by remember { mutableIntStateOf(0) }
    var tempo by remember { mutableIntStateOf(72) }
    var settingsOpen by remember { mutableStateOf(false) }
    BackHandler { onClose() }
    DisposableEffect(Unit) { onDispose { webView?.destroy(); webView = null } }
    val scheme = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回曲谱库") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("alphaTab · 统一 SVG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { webView?.evaluateJavascript("window.scoreApi && window.scoreApi.play();", null) }) { Icon(Icons.Rounded.PlayArrow, "播放") }
                IconButton(onClick = { webView?.evaluateJavascript("window.scoreApi && window.scoreApi.pause();", null) }) { Icon(Icons.Rounded.Pause, "暂停") }
                IconButton(onClick = { webView?.evaluateJavascript("window.scoreApi && window.scoreApi.stop();", null) }) { Icon(Icons.Rounded.Stop, "停止") }
                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "播放设置") }
            }
            if (settingsOpen) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("播放设置", style = MaterialTheme.typography.labelLarge)
                    Text("变调夹：${capo} 品", style = MaterialTheme.typography.bodySmall)
                    Slider(capo.toFloat(), { capo = it.toInt(); applyPlaybackSettings(webView, capo, tempo) }, valueRange = 0f..12f, steps = 11)
                    Text("速度：$tempo BPM", style = MaterialTheme.typography.bodySmall)
                    Slider(tempo.toFloat(), { tempo = it.toInt(); applyPlaybackSettings(webView, capo, tempo) }, valueRange = 40f..180f, steps = 139)
                    TextButton({ settingsOpen = false }) { Text("完成") }
                }
            }
                AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                         setBackgroundColor(AndroidColor.rgb(16, 18, 17))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        addJavascriptInterface(ScoreAssetBridge(context), "AndroidScore")
                        val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                            .build()
                        webViewClient = object : WebViewClientCompat() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: android.webkit.WebResourceRequest,
                            ): android.webkit.WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                            override fun shouldInterceptRequest(
                                view: WebView,
                                url: String,
                            ): android.webkit.WebResourceResponse? = assetLoader.shouldInterceptRequest(android.net.Uri.parse(url))
                        }
                        webChromeClient = WebChromeClient()
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_UP -> {
                                    val dx = event.x - downX
                                    val dy = event.y - downY
                                    if (dx * dx + dy * dy < 18f * 18f) {
                                        evaluateJavascript("window.handleNativeScoreTap && window.handleNativeScoreTap(${event.x} * window.innerWidth / ${view.width}, ${event.y} * window.innerWidth / ${view.width});", null)
                                    }
                                    view.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                                MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                         loadUrl("https://appassets.androidplatform.net/assets/mobile-score.html?song=$songId&bg=${scheme.background.toCssHex()}&surface=${scheme.surface.toCssHex()}&ink=${scheme.onSurface.toCssHex()}&muted=${scheme.onSurfaceVariant.toCssHex()}&line=${scheme.outline.toCssHex()}&brand=${scheme.primary.toCssHex()}&accent=${scheme.secondary.toCssHex()}")
                        webView = this
                    }
                },
                update = { webView = it },
                )
            }
        }
}

private fun androidx.compose.ui.graphics.Color.toCssHex(): String =
    "#%08X".format(toArgb())

private fun applyPlaybackSettings(webView: WebView?, capo: Int, tempo: Int) {
    webView?.evaluateJavascript("window.setMobilePlaybackSettings && window.setMobilePlaybackSettings($capo, $tempo);", null)
}

private class ScoreAssetBridge(private val context: android.content.Context) {
    @JavascriptInterface
    fun load(songId: String): String {
        val assetPath = when (songId) {
            "yu_jian", "hua" -> "$songId.json"
            "zui_lang_man_de_shi" -> "songs/zui_lang_man_de_shi.alphaTex"
            else -> return ""
        }
        return try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            ""
        }
    }
}
