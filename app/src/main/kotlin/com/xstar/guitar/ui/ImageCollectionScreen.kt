package com.xstar.guitar.ui

import android.net.Uri
import android.widget.ImageView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ImageCollectionScreen(title: String, uris: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onClose() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回曲谱库") }
                Column(Modifier.padding(start = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("图片曲谱 · ${uris.size} 页", style = MaterialTheme.typography.labelSmall)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(uris) { index, value ->
                    Text("第 ${index + 1} 页", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 720.dp).padding(horizontal = 8.dp),
                        factory = {
                            ZoomableImageView(context).apply { setImageURI(Uri.parse(value)) }
                        },
                        update = { },
                    )
                }
            }
        }
    }
}

private class ZoomableImageView(context: android.content.Context) : ImageView(context) {
    private val matrixState = Matrix()
    private val baseMatrix = Matrix()
    private var scale = 1f
    private var initialized = false
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            scale = (scale * factor).coerceIn(1f, 5f)
            matrixState.set(baseMatrix)
            matrixState.postScale(scale, scale, detector.focusX, detector.focusY)
            imageMatrix = matrixState
            return true
        }
    })
    private var lastX = 0f
    private var lastY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var touchingImage = false

    init {
        adjustViewBounds = false
        scaleType = ScaleType.MATRIX
        setBackgroundColor(android.graphics.Color.BLACK)
        isClickable = true
        setOnClickListener { if (scale > 1.01f) resetZoom() else scale = 2f; applyScale(width / 2f, height / 2f) }
        setOnTouchListener { view, event ->
            detector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    lastX = event.x
                    lastY = event.y
                    touchingImage = scale > 1.01f
                    view.parent?.requestDisallowInterceptTouchEvent(touchingImage)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    touchingImage = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex >= 0 && !detector.isInProgress && scale > 1.01f) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        matrixState.postTranslate(x - lastX, y - lastY)
                        imageMatrix = matrixState
                        lastX = x
                        lastY = y
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val releasedId = event.getPointerId(event.actionIndex)
                    if (releasedId == activePointerId) {
                        val newIndex = if (event.actionIndex == 0) 1 else 0
                        if (newIndex < event.pointerCount) {
                            activePointerId = event.getPointerId(newIndex)
                            lastX = event.getX(newIndex)
                            lastY = event.getY(newIndex)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    touchingImage = false
                }
            }
            true
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { fitImage() }
    }

    private fun fitImage() {
        if (width <= 0 || height <= 0 || drawable == null) return
        val imageWidth = drawable.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val imageHeight = drawable.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val fit = minOf(width / imageWidth, height / imageHeight)
        baseMatrix.reset()
        baseMatrix.setScale(fit, fit)
        baseMatrix.postTranslate((width - imageWidth * fit) / 2f, (height - imageHeight * fit) / 2f)
        resetZoom()
        initialized = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitImage() }
    }

    private fun resetZoom() {
        scale = 1f
        matrixState.set(baseMatrix)
        imageMatrix = matrixState
    }

    private fun applyScale(x: Float, y: Float) {
        matrixState.set(baseMatrix)
        matrixState.postScale(scale, scale, x, y)
        imageMatrix = matrixState
    }
}
