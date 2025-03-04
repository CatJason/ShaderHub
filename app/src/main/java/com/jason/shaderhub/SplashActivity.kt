package com.jason.shaderhub

import android.content.Intent
import android.os.Bundle
import android.window.SplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            SplashScreen {
                startActivity(Intent(context, MainActivity::class.java))
                finish()
            }
        }
    }

    @Composable
    fun SplashScreen(navigateToMain: () -> Unit) {

        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.toFloat()
        val screenHeight = configuration.screenHeightDp.toFloat()

        var scale by remember { mutableStateOf(1f) }

        // 动画过渡，使图片在 1s 内从 1.0 放大到 2.0
        val animatedScale by animateFloatAsState(
            targetValue = scale,
            animationSpec = tween(durationMillis = 1000), // 1s 动画
            label = "Scale Animation"
        )

        LaunchedEffect(Unit) {
            delay(100) // 稍微延迟，让界面先加载
            scale = (screenHeight / screenWidth) * 1.2f
            delay(1000L) // 1s 后跳转
            navigateToMain()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black), // 设置黑色背景
            contentAlignment = Alignment.Center
        ) {
            // 放大动画
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = "App Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp) // 左右 12dp 间距
                    .scale(animatedScale) // **添加缩放动画**
            )
        }
    }
}
