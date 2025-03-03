package com.jason.shaderhub

import android.opengl.GLSurfaceView

interface IRender: GLSurfaceView.Renderer {
    fun rotate(deltaX: Float, deltaY: Float)
}