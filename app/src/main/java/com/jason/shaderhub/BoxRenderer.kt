package com.jason.shaderhub

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BoxRenderer : IRender {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var shaderProgram = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpMatrixHandle = 0
    private var boxXHandle = 0
    private var boxYHandle = 0
    private var boxZHandle = 0

    private var rotationX = 0f
    private var rotationY = 0f

    companion object {
        private const val BOX_X = 2.0f
        private const val BOX_Y = 1.0f
        private const val BOX_Z = 2.0f
    }

    override fun rotate(dx: Float, dy: Float) {
        rotationY += dx
        rotationX -= dy
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vertexShaderCode = """
            #version 300 es
            in vec4 a_Position;
            in vec3 a_Normal;
            uniform mat4 u_MVPMatrix;
            out vec3 fragNormal;
            void main() {
                gl_Position = u_MVPMatrix * a_Position;
                fragNormal = a_Normal;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            #version 300 es
            precision mediump float;
            in vec3 fragNormal;
            out vec4 fragColor;
            uniform float box_x;
            uniform float box_y;
            uniform float box_z;
            
            vec4 intersect_box(vec3 ro, vec3 rd) {
                float t_min = 1000.0;
                vec3 t_normal;
                float t = (-box_x - ro.x) / rd.x;
                vec3 p = ro + t * rd;
                if (p.y > -box_y && p.z < box_z && p.z > -box_z) {
                    t_normal = vec3(-1.0, 0.0, 0.0);
                    t_min = t;
                }
                t = (box_x - ro.x) / rd.x;
                p = ro + t * rd;
                if (p.y > -box_y && p.z < box_z && p.z > -box_z) {
                    if (t < t_min) {
                        t_normal = vec3(1.0, 0.0, 0.0);
                        t_min = t;
                    }
                }
                t = (-box_z - ro.z) / rd.z;
                p = ro + t * rd;
                if (p.y > -box_y && p.x < box_x && p.x > -box_x) {
                    if (t < t_min) {
                        t_normal = vec3(0.0, 0.0, -1.0);
                        t_min = t;
                    }
                }
                t = (box_z - ro.z) / rd.z;
                p = ro + t * rd;
                if (p.y > -box_y && p.x < box_x && p.x > -box_x) {
                    if (t < t_min) {
                        t_normal = vec3(0.0, 0.0, 1.0);
                        t_min = t;
                    }
                }
                if (t_min < 1000.0) return vec4(0.4, 0.6, 1.0, 1.0);
                return vec4(0.0, 0.0, 0.0, 1.0);
            }
            
            void main() {
                vec3 rayOrigin = vec3(0.0, 0.0, -5.0);
                vec3 rayDir = normalize(fragNormal);
                fragColor = intersect_box(rayOrigin, rayDir);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        normalHandle = GLES20.glGetAttribLocation(shaderProgram, "a_Normal")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "u_MVPMatrix")
        boxXHandle = GLES20.glGetUniformLocation(shaderProgram, "box_x")
        boxYHandle = GLES20.glGetUniformLocation(shaderProgram, "box_y")
        boxZHandle = GLES20.glGetUniformLocation(shaderProgram, "box_z")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)

        Matrix.perspectiveM(projectionMatrix, 0, 45f, 1f, 1f, 100f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -5f, 0f, 0f, 0f, 0f, 1f, 0f)

        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

        val mvpMatrix = FloatArray(16).apply {
            Matrix.multiplyMM(this, 0, projectionMatrix, 0, viewMatrix, 0)
            Matrix.multiplyMM(this, 0, this, 0, modelMatrix, 0)
        }

        GLES20.glUseProgram(shaderProgram)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(boxXHandle, BOX_X)
        GLES20.glUniform1f(boxYHandle, BOX_Y)
        GLES20.glUniform1f(boxZHandle, BOX_Z)

        val vertices = floatArrayOf(
            // Front face
            -1f, -1f, 1f, 0f, 0f, 1f,
            1f, -1f, 1f, 0f, 0f, 1f,
            1f, 1f, 1f, 0f, 0f, 1f,
            -1f, 1f, 1f, 0f, 0f, 1f,

            // Back face
            -1f, -1f, -1f, 0f, 0f, -1f,
            1f, -1f, -1f, 0f, 0f, -1f,
            1f, 1f, -1f, 0f, 0f, -1f,
            -1f, 1f, -1f, 0f, 0f, -1f,

            // Left face
            -1f, -1f, -1f, -1f, 0f, 0f,
            -1f, -1f, 1f, -1f, 0f, 0f,
            -1f, 1f, 1f, -1f, 0f, 0f,
            -1f, 1f, -1f, -1f, 0f, 0f,

            // Right face
            1f, -1f, -1f, 1f, 0f, 0f,
            1f, -1f, 1f, 1f, 0f, 0f,
            1f, 1f, 1f, 1f, 0f, 0f,
            1f, 1f, -1f, 1f, 0f, 0f
        )

        val indices = shortArrayOf(
            0, 1, 2, 2, 3, 0,
            4, 5, 6, 6, 7, 4,
            8, 9, 10, 10, 11, 8,
            12, 13, 14, 14, 15, 12
        )

        val vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        val indexBuffer = ByteBuffer
            .allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }

        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            6 * 4,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        GLES20.glVertexAttribPointer(
            normalHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            6 * 4,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(normalHandle)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indices.size,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}