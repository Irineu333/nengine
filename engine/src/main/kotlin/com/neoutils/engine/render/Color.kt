package com.neoutils.engine.render

data class Color(val r: Float, val g: Float, val b: Float, val a: Float = 1f) {

    companion object {
        val BLACK: Color = Color(0f, 0f, 0f)
        val WHITE: Color = Color(1f, 1f, 1f)
        val RED: Color = Color(1f, 0f, 0f)
        val GREEN: Color = Color(0f, 1f, 0f)
        val BLUE: Color = Color(0f, 0f, 1f)
        val GRAY: Color = Color(0.5f, 0.5f, 0.5f)
        val TRANSPARENT: Color = Color(0f, 0f, 0f, 0f)
    }
}
