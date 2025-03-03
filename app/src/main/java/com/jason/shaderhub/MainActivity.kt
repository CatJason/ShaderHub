package com.jason.shaderhub

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jason.shaderhub.ui.theme.ShaderHubTheme
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.ui.viewinterop.AndroidView

// MainActivity.kt
class MainActivity : ComponentActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var gestureDetector: GestureDetector
    private lateinit var renderer: CardRenderer
    private var translationX = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化渲染器
        renderer = CardRenderer()

        // 配置GLSurfaceView
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY // 启用按需渲染
        }

        // 手势处理
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // 更新水平平移量
                translationX -= distanceX
                renderer.updateTranslation(translationX)
                requestRender()
                return true
            }
        })

        setContent {
            ShaderHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { glSurfaceView },
                        update = { view ->
                            view.setOnTouchListener { _, event ->
                                gestureDetector.onTouchEvent(event)
                                true
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestRender() {
        glSurfaceView.requestRender()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }
}