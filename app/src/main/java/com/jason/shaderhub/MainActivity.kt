package com.jason.shaderhub

import android.annotation.SuppressLint
import android.os.Bundle
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jason.shaderhub.CardRenderer.Companion.CARD_COUNT
import com.jason.shaderhub.ui.theme.ShaderHubTheme

class MainActivity : ComponentActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: CardRenderer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化带物理效果的CardRenderer
        renderer = CardRenderer(this).apply {
            // 可在此配置卡片数量等参数
            CARD_COUNT = 5
        }

        // 配置高性能GLSurfaceView
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY // 启用持续渲染
        }

        setContent {
            ShaderHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { glSurfaceView },
                        update = { view ->
                            // 触摸事件处理绑定
                            view.setOnTouchListener { _, event ->
                                handleTouchEvent(event)
                                true
                            }
                        }
                    )
                }
            }
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        // 将事件传递给渲染器的触摸处理器
        renderer.handleTouchEvent(event)
        return true
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