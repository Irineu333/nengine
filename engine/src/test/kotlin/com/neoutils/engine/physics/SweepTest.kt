package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun circle(r: Float): CircleShape2D = CircleShape2D().apply { radius = r }
private fun rect(size: Vec2): RectangleShape2D = RectangleShape2D().apply { this.size = size }

private const val EPS = 0.001f

class SweepTest {

    @Test
    fun `swept circle-circle hits at TOI 0_1 with normal pointing from B toward A`() {
        val a = circle(5f); val b = circle(5f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(12f, 0f))
        val r = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(r)
        assertEquals(0.1f, r.toi, EPS)
        assertEquals(-1f, r.normal.x, EPS)
        assertEquals(0f, r.normal.y, EPS)
    }

    @Test
    fun `swept circle-rect axis-aligned hits at TOI 0_3 on left face`() {
        val c = circle(3f); val r = rect(Vec2(4f, 4f))
        // Centered rect at (8,0) spans [6,10]×[-2,2]; its left face is x=6.
        // Circle r=3 from x=0 touches it when cx+3=6 → cx=3, t=3/10=0.3.
        val cWorld = Transform(position = Vec2(0f, 0f))
        val rWorld = Transform(position = Vec2(8f, 0f))
        val res = sweepOverlap(c, cWorld, Vec2(10f, 0f), r, rWorld)
        assertNotNull(res)
        assertEquals(0.3f, res.toi, EPS)
        assertEquals(-1f, res.normal.x, EPS)
        assertEquals(0f, res.normal.y, EPS)
    }

    @Test
    fun `swept rect-rect axis-aligned hits at TOI 0_3 on left face`() {
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(10f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(0.3f, res.toi, EPS)
        assertEquals(-1f, res.normal.x, EPS)
        assertEquals(0f, res.normal.y, EPS)
    }

    @Test
    fun `swept with no intersection returns null`() {
        val a = circle(5f); val b = circle(5f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(0f, 100f))
        // Motion parallel to x-axis, target far on y — never touches.
        assertNull(sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld))
    }

    @Test
    fun `swept rotated rect that misses on a far axis returns null`() {
        // After kinematic-rotated-sweep, rotated rects no longer bail out;
        // null now only comes from actual geometric miss.
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = (PI / 4.0).toFloat())
        val bWorld = Transform(position = Vec2(0f, 200f), rotation = (PI / 4.0).toFloat())
        assertNull(sweepOverlap(a, aWorld, Vec2(40f, 0f), b, bWorld))
    }

    @Test
    fun `starting-overlap circle-circle reports TOI 0 with separation normal`() {
        val a = circle(5f); val b = circle(5f)
        // Centers 4 units apart, both radius 5 — already overlapping.
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(4f, 0f))
        val r = sweepOverlap(a, aWorld, Vec2(10f, 0f), b, bWorld)
        assertNotNull(r)
        assertEquals(0f, r.toi)
        // Separation points from B toward A — A is to the left of B, so normal.x < 0.
        assertTrue(r.normal.x < 0f, "expected separation normal pointing left, got ${r.normal}")
    }

    @Test
    fun `starting-overlap rect-rect reports TOI 0 with separation along smallest pen axis`() {
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        // A at (0,0), B at (3,0) — strong overlap on x (7), full overlap on y (10).
        // Smallest penetration: push left out of B (3 to clear left edge).
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(3f, 0f))
        val r = sweepOverlap(a, aWorld, Vec2(0f, 0f), b, bWorld)
        assertNotNull(r)
        assertEquals(0f, r.toi)
        assertEquals(-1f, r.normal.x, EPS)
    }

    @Test
    fun `rect tangent to wall moving outward does not report a bogus collision`() {
        // Reproduces the freeze pattern: ball sits exactly at a wall's face
        // moving AWAY. The slab method's tEnter is in the past (<= 0) and
        // tExit == 0; without the tangent-leaving guard this would be reported
        // as toi=0 with the wall's inward normal, causing the script to
        // reflect velocity back into the wall every frame.
        val ball = rect(Vec2(12f, 12f))
        val wall = rect(Vec2(10f, 600f))
        // Centered shapes: wall at (-10, 0) spans [-15, -5] (right edge x=-5).
        // Ball at (1, 100) spans [-5, 7] → its left edge x=-5 touches the wall's
        // right edge. Moving RIGHT (away from the wall).
        val ballWorld = Transform(position = Vec2(1f, 100f))
        val wallWorld = Transform(position = Vec2(-10f, 0f))
        val motion = Vec2(50f, 0f) // moving away to the right
        assertNull(sweepOverlap(ball, ballWorld, motion, wall, wallWorld))
    }

    @Test
    fun `circle tangent to rect moving outward does not report a bogus collision`() {
        val c = circle(6f)
        val r = rect(Vec2(10f, 600f))
        // Centered rect at (-10, 0) spans [-15, -5] → right edge x=-5. A circle
        // of radius 6 is tangent to that edge when cx - 6 = -5 → cx = 1. Moving
        // right (away from the rect).
        val cWorld = Transform(position = Vec2(1f, 100f))
        val rWorld = Transform(position = Vec2(-10f, 0f))
        val motion = Vec2(80f, 0f)
        assertNull(sweepOverlap(c, cWorld, motion, r, rWorld))
    }

    @Test
    fun `swept rect-vs-circle is symmetric with circle-vs-rect`() {
        val r = rect(Vec2(4f, 4f)); val c = circle(3f)
        // Rect at (0,0) moves right by (20,0); circle stationary at (10, 2).
        val rWorld = Transform(position = Vec2(0f, 0f))
        val cWorld = Transform(position = Vec2(10f, 2f))
        val res = sweepOverlap(r, rWorld, Vec2(20f, 0f), c, cWorld)
        assertNotNull(res)
        // Normal points from circle outward toward rect (mover).
        // Rect moves +x, hits circle's left side → normal points -x.
        assertTrue(res.normal.x < 0f, "expected normal pointing left, got ${res.normal}")
    }

    // --- kinematic-rotated-sweep scenarios ---

    @Test
    fun `swept circle-vs-rotated-rect 90deg hits at analytic TOI`() {
        // Centered 4x4 rect at (10,0) rotated 90° around its center maps onto
        // itself: occupies world x ∈ [8,12], y ∈ [-2,2]. Circle r=2 at (0,0)
        // moves +x by 20. Tangency when circle right edge (cx + 2) reaches the
        // rect's leftmost world x = 8 → cx = 6, t = 6/20 = 0.3.
        val c = circle(2f); val r = rect(Vec2(4f, 4f))
        val cWorld = Transform(position = Vec2(0f, 0f))
        val rWorld = Transform(position = Vec2(10f, 0f), rotation = (PI / 2.0).toFloat())
        val res = sweepOverlap(c, cWorld, Vec2(20f, 0f), r, rWorld)
        assertNotNull(res)
        assertEquals(0.3f, res.toi, 0.01f)
        // Normal points from rect outward toward circle → -x in world.
        assertEquals(-1f, res.normal.x, 0.05f)
    }

    @Test
    fun `swept rotated-rect-vs-rotated-rect same rotation 45deg face-to-face contact`() {
        // Two 4x4 centered rects rotated 45° (diamonds in world). A at (0,0)
        // reaches B at (10,0) via motion (20,0). A's projection on its own
        // edge1 = (cos45,sin45) is [-2,2] (half-width 2). B's projection on the
        // same axis is centered at 10·cos45 ≈ 7.07 → min ≈ 5.07. Contact at
        // t = (5.07-2)/14.14 ≈ 0.217 (unchanged: both intervals shift by -2).
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(10f, 0f), rotation = rot)
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertTrue(res.toi > 0f && res.toi < 1f, "expected TOI in (0,1); got ${res.toi}")
        assertEquals(0.217f, res.toi, 0.02f)
        // Some component of the normal must oppose A's motion (+x).
        assertTrue(res.normal.x < 0f, "expected normal x < 0; got ${res.normal}")
    }

    @Test
    fun `swept rotated-rect-vs-rotated-rect different rotation collides with valid TOI`() {
        // A axis-aligned centered at (0,0) → right edge x=2; B rotated 45° at
        // (10,0) → leftmost vertex at x=7.17 (= 10 - 2·√2). Contact between A's
        // face and B's vertex at some t in (0,1).
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(10f, 0f), rotation = (PI / 4.0).toFloat())
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertTrue(res.toi >= 0f && res.toi < 1f, "expected TOI in [0,1); got ${res.toi}")
        assertTrue(res.normal.x < 0f, "expected normal x < 0; got ${res.normal}")
    }

    @Test
    fun `swept rotated motion parallel to separator axis returns null`() {
        // Two 4x4 centered rects rotated 45°. A at (0,0) projects to [-2,2] on
        // axis2; B offset along A's axis2 by 8 units (B at axis2·8 = (-5.66,
        // 5.66)) projects to [6,10] there — separated. Motion (10,10) is purely
        // along axis1, so dt=0 on axis2: it never closes that gap → null.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(-5.66f, 5.66f), rotation = rot)
        val res = sweepOverlap(a, aWorld, Vec2(10f, 10f), b, bWorld)
        assertNull(res)
    }

    @Test
    fun `swept rotated with zero motion returns null when separated`() {
        // Motion zero between rotated rects far apart → static SAT separator
        // with dt==0 on every axis → null.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(100f, 0f), rotation = rot)
        assertNull(sweepOverlap(a, aWorld, Vec2.ZERO, b, bWorld))
    }

    @Test
    fun `swept rotated tangent contact moving away returns null`() {
        // Two 4x4 centered rects rotated 45°. A at (0,0) projects to [-2,2] on
        // axis1. B at (2.83, 2.83) projects there centered at 4 → [2,6], whose
        // min (2) equals A's max (2): tangent on axis1. Motion away from B
        // (= -axis1 direction) should NOT trigger a collision.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val rot = (PI / 4.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(2.83f, 2.83f), rotation = rot)
        // Move away: -axis1 = (-cos45, -sin45) × 20 ≈ (-14.14, -14.14).
        assertNull(sweepOverlap(a, aWorld, Vec2(-14.14f, -14.14f), b, bWorld))
    }

    // --- add-rigid-body-2d: geometric contact point ---

    @Test
    fun `point lies on circle A surface for circle-vs-circle hit`() {
        // A at (0,0) r=5, motion (20,0), B at (12,0) r=5. Toi=0.1 → A at (2,0).
        // Contact point on A's surface in direction of B = (7, 0).
        val a = circle(5f); val b = circle(5f)
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(12f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(7f, res.point.x, EPS)
        assertEquals(0f, res.point.y, EPS)
    }

    @Test
    fun `point is on rect face for axis-aligned rect-vs-rect`() {
        // A at (0,0) size 4x4 swept right by (20,0) into B at (10,0) size 4x4,
        // both centered. B spans [8,12]×[-2,2], so its left face is x=8; the
        // overlap on y is [-2,2] → mid y=0.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f))
        val bWorld = Transform(position = Vec2(10f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(8f, res.point.x, EPS)
        assertEquals(0f, res.point.y, EPS)
    }

    @Test
    fun `point is leading corner for rotated rect-vs-rect`() {
        // A 4x4 centered at (0,0) rotated 45° swept by (20,0) into axis-aligned
        // B 4x4 centered at (10,0). A is a diamond centered on the origin with
        // vertices (±2.83, 0), (0, ±2.83); B spans [8,12]×[-2,2].
        // Leading corner (smallest projection on normal ≈ (-1,0)) → max x at contact.
        val a = rect(Vec2(4f, 4f)); val b = rect(Vec2(4f, 4f))
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = (PI / 4.0).toFloat())
        val bWorld = Transform(position = Vec2(10f, 0f))
        val res = sweepOverlap(a, aWorld, Vec2(20f, 0f), b, bWorld)
        assertNotNull(res)
        // Result point should NOT be A's center; it lies on A's leading corner
        // in the -normal direction. Since normal points toward A (-x), -normal
        // = +x: the leading corner is A's rightmost vertex at contact, near B's
        // left face x=8.
        assertTrue(res.point.x > 5f, "leading corner should be near B's face, got ${res.point.x}")
    }

    @Test
    fun `swept rotated starting overlap reports TOI 0 with MTV depenetration`() {
        // Deep overlap: B's origin at (2,0) rotated 45°, while A also rotated
        // 45° at (0,0). Both diamond-shaped, heavily overlapping.
        val a = rect(Vec2(10f, 10f)); val b = rect(Vec2(10f, 10f))
        val rot = (PI / 6.0).toFloat()
        val aWorld = Transform(position = Vec2(0f, 0f), rotation = rot)
        val bWorld = Transform(position = Vec2(5f, 0f), rotation = rot)
        val res = sweepOverlap(a, aWorld, Vec2(0f, 0f), b, bWorld)
        assertNotNull(res)
        assertEquals(0f, res.toi)
        // Depenetration should be non-zero (separation vector).
        assertTrue(res.depenetration.length > 0f, "expected non-zero MTV; got ${res.depenetration}")
        // Depenetration pushes A away from B → projection onto (A.pos - B.pos)
        // direction should be positive.
        val awayDir = Vec2(-1f, 0f) // A is to the left of B in this setup
        val dot = res.depenetration.x * awayDir.x + res.depenetration.y * awayDir.y
        assertTrue(dot > 0f, "expected depenetration to push A away from B; got ${res.depenetration}")
    }
}
