package com.jason.shaderhub

import android.os.Bundle
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jason.shaderhub.ui.theme.ShaderHubTheme
import android.opengl.GLSurfaceView
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建 GLSurfaceView 实例
        val glSurfaceView = GLSurfaceView(this)
        glSurfaceView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 设置 OpenGL 渲染器为海洋纹理渲染器
        glSurfaceView.setEGLContextClientVersion(2) // 设置 OpenGL ES 版本
        glSurfaceView.setRenderer(FogCubeTextureRenderer()) // 设置自定义渲染器

        setContent {
            ShaderHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 嵌套 GLSurfaceView
                    AndroidView({ glSurfaceView })
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShaderHubTheme {
        Greeting("Android")
    }
}
