package com.jason.shaderhub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Movie
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.Toast
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

class CardRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object {
        var CARD_COUNT = 5
        private const val SCROLL_FRICTION = 0.95f
        private const val EDGE_SPRING = 0.3f
        private const val TOUCH_SLOP = 0.5f
    }

    // OpenGL 相关变量
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer
    private var mProgram = 0
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val textures = IntArray(CARD_COUNT)
    private val starTextures = mutableListOf<Int>() // 存储所有帧的纹理
    private var currentFrame = 0 // 当前帧索引
    private var frameDelay = 100L // 帧间隔时间（毫秒）
    private var lastFrameTime = System.currentTimeMillis() // 上一帧时间
    private var starAlpha = 0.5f // 星星纹理的透明度

    // 屏幕参数
    private var screenWidth = 0
    private var screenHeight = 0
    private val density = context.resources.displayMetrics.density

    // 滚动参数
    private var translationX = 0f
    private var currentVelocity = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    // 物理滚动工具
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val velocityTracker = VelocityTracker.obtain()
    private val scroller = android.widget.Scroller(context)

    // 顶点着色器
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec2 vPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * vec4(vPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // 片段着色器
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture; // 卡片纹理
        uniform sampler2D uStarTexture; // 星星纹理
        uniform float uStarAlpha; // 星星纹理的透明度
        void main() {
            vec4 cardColor = texture2D(uTexture, vTexCoord); // 获取卡片颜色
            vec4 starColor = texture2D(uStarTexture, vTexCoord); // 获取星星颜色
            gl_FragColor = mix(cardColor, starColor, starColor.a * uStarAlpha); // 混合颜色
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        initShaders()
        Matrix.setIdentityM(modelMatrix, 0)
        loadTextures(context)
        loadStarGif(context) // 加载 GIF 文件并解析为帧
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), 0f, height.toFloat(), -1f, 1f)
        generateVertexData()
        generateTextureData()
    }

    override fun onDrawFrame(gl: GL10) {
        updatePhysics()
        applyEdgeSpring()

        // 更新当前帧
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime >= frameDelay) {
            currentFrame = (currentFrame + 1) % starTextures.size
            lastFrameTime = currentTime
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(mProgram)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, -translationX, 0f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)

        val matrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvpMatrix, 0)

        drawCards()
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastTouchX = event.x
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                isDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.x - lastTouchX) * TOUCH_SLOP
                translationX -= deltaX * density
                translationX = translationX.coerceIn(0f, getMaxTranslation())
                lastTouchX = event.x
                velocityTracker.addMovement(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val velocityX = velocityTracker.xVelocity

                if (abs(velocityX) > minFlingVelocity) {
                    scroller.fling(
                        translationX.toInt(), 0,
                        (-velocityX).toInt(), 0,
                        0, getMaxTranslation().toInt(),
                        0, 0
                    )
                }
                velocityTracker.clear()
            }
        }
        return true
    }

    private fun updatePhysics() {
        if (!isDragging && !scroller.isFinished) {
            if (scroller.computeScrollOffset()) {
                translationX = scroller.currX.toFloat()
            } else {
                currentVelocity *= SCROLL_FRICTION
                translationX += currentVelocity * density
                translationX = translationX.coerceIn(0f, getMaxTranslation())
                if (abs(currentVelocity) < 0.5f) currentVelocity = 0f
            }
        }
    }

    private fun applyEdgeSpring() {
        val max = getMaxTranslation()
        when {
            translationX < 0 -> translationX -= translationX * EDGE_SPRING
            translationX > max -> translationX -= (translationX - max) * EDGE_SPRING
        }
    }

    private fun drawCards() {
        val positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture")
        val starTextureHandle = GLES20.glGetUniformLocation(mProgram, "uStarTexture")
        val starAlphaHandle = GLES20.glGetUniformLocation(mProgram, "uStarAlpha")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        repeat(CARD_COUNT) { i ->
            // 绑定卡片纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glUniform1i(textureHandle, 0)

            // 绑定当前帧的星星纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, starTextures[currentFrame])
            GLES20.glUniform1i(starTextureHandle, 1)

            // 设置星星透明度
            GLES20.glUniform1f(starAlphaHandle, starAlpha)

            // 绘制卡片
            vertexBuffer.position(i * 8)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            textureBuffer.position(0)
            GLES20.glVertexAttribPointer(
                texCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                textureBuffer
            )

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun generateVertexData() {
        val cardWidth = screenWidth * 4f / 5f
        val cardHeight = cardWidth * 16f / 9f
        val spacing = screenWidth * 1f / 10f
        val startX = spacing  // 左边距

        // 生成顶点数据
        val vertices = FloatArray(CARD_COUNT * 8).apply {
            for (i in 0 until CARD_COUNT) {
                val left = startX + i * (cardWidth + spacing)
                val right = left + cardWidth
                val top = (screenHeight - cardHeight) / 2f // 垂直居中
                val bottom = top + cardHeight

                val offset = i * 8
                this[offset] = left       // 左上x
                this[offset + 1] = top    // 左上y
                this[offset + 2] = left   // 左下x
                this[offset + 3] = bottom // 左下y
                this[offset + 4] = right  // 右上x
                this[offset + 5] = top    // 右上y
                this[offset + 6] = right // 右下x
                this[offset + 7] = bottom // 右下y
            }
        }

        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices).position(0) }
    }

    private fun generateTextureData() {
        val textureCoords = floatArrayOf(
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
        )

        textureBuffer = ByteBuffer
            .allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(textureCoords).position(0) }
    }

    private fun getMaxTranslation(): Float {
        if (screenWidth == 0) return 0f
        val cardWidth = screenWidth * 4f / 5f
        val spacing = screenWidth * 1f / 10f
        val totalWidth = CARD_COUNT * cardWidth + (CARD_COUNT + 1) * spacing
        return (totalWidth - screenWidth).coerceAtLeast(0f)
    }

    private fun initShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadTextures(context: Context) {
        for (i in 0 until CARD_COUNT) {
            val resourceId =
                context.resources.getIdentifier("pic$i", "drawable", context.packageName)
            textures[i] = loadTexture(context, resourceId)
        }
    }

    private fun loadTexture(context: Context, resourceId: Int): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)

            // 翻转图片的 Y 坐标
            val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
            val flippedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flippedBitmap, 0)
            flippedBitmap.recycle()
            bitmap.recycle()
        }

        return textureHandle[0]
    }

    private fun loadStarGif(context: Context) {
        val inputStream: InputStream = context.assets.open("star.gif")
        val movie = Movie.decodeStream(inputStream)
        val frameCount = movie.duration() / 100 // 假设每帧持续 100 毫秒

        (context as MainActivity).runOnUiThread {
            // 显示帧数
            Toast.makeText(context, "GIF 帧数: $frameCount", Toast.LENGTH_SHORT).show()
        }

        for (i in 0 until frameCount) {
            val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            movie.setTime(i * 100) // 设置当前帧时间
            movie.draw(canvas, 0f, 0f)

            val textureHandle = IntArray(1)
            GLES20.glGenTextures(1, textureHandle, 0)

            if (textureHandle[0] != 0) {
                // 翻转图片的 Y 坐标
                val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
                val flippedBitmap =
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
                )
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flippedBitmap, 0)
                flippedBitmap.recycle()
                bitmap.recycle()
            }

            starTextures.add(textureHandle[0])
        }

        inputStream.close()
    }
}