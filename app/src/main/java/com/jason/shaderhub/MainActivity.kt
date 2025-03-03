package com.jason.shaderhub

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jason.shaderhub.ui.theme.ShaderHubTheme
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var gestureDetector: GestureDetector
    private lateinit var mRenderer: IRender

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建 GLSurfaceView 实例
        glSurfaceView = GLSurfaceView(this)
        glSurfaceView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 创建 BoxRenderer 实例
        mRenderer = GradientTriangleRenderer()

        // 设置 OpenGL 渲染器为海洋纹理渲染器
        glSurfaceView.setEGLContextClientVersion(2) // 设置 OpenGL ES 版本
        glSurfaceView.setRenderer(mRenderer) // 设置自定义渲染器

        // 创建 GestureDetector 以监听手势
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // 调用渲染器的 rotate 方法
                mRenderer.rotate(distanceX, - distanceY)
                return true
            }
        })

        setContent {
            ShaderHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 嵌套 GLSurfaceView
                    AndroidView({ glSurfaceView }) { view ->
                        // 处理触摸事件并传递给 GestureDetector
                        view.setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
                            true
                        }
                    }
                }
            }
        }
    }
}

