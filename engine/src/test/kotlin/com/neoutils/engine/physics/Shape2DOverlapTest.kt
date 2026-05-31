package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun rect(size: Float): RectangleShape2D =
    RectangleShape2D().apply { this.size = Vec2(size, size) }

private val QUARTER_TURN: Float = (PI / 4.0).toFloat()

class Shape2DOverlapTest {

    // Local origin is the geometric center, so a rectangle of size 20 at
    // world.position p spans [p.x-10, p.x+10] × [p.y-10, p.y+10] when
    // rotation == 0. The scenarios in the spec are described with that
    // convention.

    @Test
    fun `rotated rectangles with AABBs overlapping but OBBs separated do not overlap`() {
        val a = rect(20f)
        val b = rect(20f)
        // Both squares are centered, so A's rotated diamond has corners
        // (0, ∓14.14), (±14.14, 0) and spans [-14.14, 14.14]². Translating B
        // by (15, 15) puts the center separation along the shared edge normal
        // (1,1)/√2 at ~21.21 — beyond the combined apothems 10+10=20, so SAT
        // separates on that axis. The AABB envelopes (each 28.28×28.28, A on
        // [-14.14, 14.14]² and B on [0.86, 29.14]²) still overlap on
        // [0.86, 14.14]².
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val bWorld = Transform(position = Vec2(15f, 15f), rotation = QUARTER_TURN)

        assertTrue(
            a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO)),
            "AABB envelopes should overlap (precondition of the regression test)",
        )

        assertFalse(overlap(a, aWorld, b, bWorld))
    }

    @Test
    fun `rotated rectangles whose OBBs actually overlap return true`() {
        val a = rect(20f)
        val b = rect(20f)
        // Identical rotation and a small offset along the rotated frame —
        // OBBs deeply overlap.
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val bWorld = Transform(position = Vec2(10f, 10f), rotation = QUARTER_TURN)

        assertTrue(overlap(a, aWorld, b, bWorld))
    }

    @Test
    fun `axis-aligned rectangles preserve existing AABB behavior`() {
        val a = rect(10f)
        val b = rect(10f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorldNear = Transform(position = Vec2(5f, 5f))
        val bWorldFar = Transform(position = Vec2(100f, 100f))

        assertTrue(overlap(a, aWorld, b, bWorldNear))
        assertFalse(overlap(a, aWorld, b, bWorldFar))
    }

    @Test
    fun `mixed rotated-and-axis-aligned uses OBB path`() {
        val a = rect(20f)
        val b = rect(20f)
        // A is a 20×20 centered square rotated 45° around the world origin, so
        // its OBB is a diamond with corners (0, ∓14.14), (±14.14, 0) — AABB
        // envelope [-14.14, 14.14]², apothem 10 along its (±1,1)/√2 normals.
        // B is axis-aligned centered at (18, 18), occupying [8, 28] × [8, 28].
        // Envelopes overlap on [8, 14.14]²; but along A's edge normal (1,1)/√2
        // A projects to [-10, 10] while B projects to [11.3, 39.6], so the OBBs
        // are separated on that axis.
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val bWorld = Transform(position = Vec2(18f, 18f), rotation = 0f)

        assertTrue(
            a.bounds(aWorld, Vec2.ZERO).intersects(b.bounds(bWorld, Vec2.ZERO)),
            "AABB envelopes should overlap (precondition for OBB-path exercise)",
        )

        assertFalse(overlap(a, aWorld, b, bWorld))
    }

    // Regression for the post-refactor contract of `RectangleShape2D.bounds`.
    @Test
    fun `bounds returns the axis-aligned rectangle when rotation is zero`() {
        val r = rect(10f)
        val world = Transform(position = Vec2(3f, 7f))
        val b = r.bounds(world, Vec2.ZERO)
        // Centered: the 10×10 rect at (3,7) spans [-2,8] × [2,12].
        assertEquals(-2f, b.origin.x)
        assertEquals(2f, b.origin.y)
        assertEquals(10f, b.size.x)
        assertEquals(10f, b.size.y)
    }

    @Test
    fun `bounds returns the AABB envelope of the rotated corners when rotation is non-zero`() {
        val r = rect(10f)
        val world = Transform(position = Vec2(0f, 0f), rotation = QUARTER_TURN)
        val b = r.bounds(world, Vec2.ZERO)
        // Centered local corners (±5,±5) rotated 45° around origin:
        //   (5,-5) → (7.07, 0)
        //   (5,5) → (0, 7.07)
        //   (-5,5) → (-7.07, 0)
        //   (-5,-5) → (0, -7.07)
        // Envelope: x ∈ [-7.07, 7.07], y ∈ [-7.07, 7.07].
        val half = 10f * sqrt(2f) / 2f
        approx(-half, b.origin.x, "origin.x")
        approx(-half, b.origin.y, "origin.y")
        approx(2f * half, b.size.x, "size.x")
        approx(2f * half, b.size.y, "size.y")
    }

    private fun approx(expected: Float, actual: Float, label: String) {
        assertTrue(
            kotlin.math.abs(expected - actual) < 1e-3f,
            "$label expected $expected, got $actual",
        )
    }
}
