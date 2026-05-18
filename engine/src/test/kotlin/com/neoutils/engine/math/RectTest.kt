package com.neoutils.engine.math

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectTest {

    @Test
    fun `overlapping rects intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(5f, 5f), Vec2(10f, 10f))
        assertTrue(a.intersects(b))
        assertTrue(b.intersects(a))
    }

    @Test
    fun `disjoint rects on x axis do not intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(20f, 0f), Vec2(10f, 10f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun `disjoint rects on y axis do not intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(0f, 20f), Vec2(10f, 10f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun `edge-touching rects do not intersect`() {
        val a = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        val b = Rect(Vec2(10f, 0f), Vec2(10f, 10f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun `contains returns true for points inside`() {
        val r = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        assertTrue(r.contains(Vec2(5f, 5f)))
    }

    @Test
    fun `contains returns false for points outside`() {
        val r = Rect(Vec2(0f, 0f), Vec2(10f, 10f))
        assertFalse(r.contains(Vec2(15f, 5f)))
    }
}
