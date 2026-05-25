package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Enxame de quadrados com velocidade linear **e** angular, colidindo
 * elasticamente contra paredes e entre si com **resposta rotacional por
 * impulso + fricção de Coulomb** — o tipo de comportamento que Demo 4
 * (reflect-pela-normal) não consegue expressar.
 *
 * Cada contato em ponto P com normal `n` aplica dois impulsos:
 *
 *  - **Normal** (e = 1, elástico):
 *    `jn = -2·(v_rel·n) / (1/mA + 1/mB + (rA×n)²/IA + (rB×n)²/IB)`
 *  - **Tangencial** (fricção de Coulomb, capped):
 *    `jt = min(velTang/denomT, MU·|jn|)`, aplicado oposto à direção de
 *    deslizamento.
 *
 * Com `rA = P - centroA` e `vAP = vA + ω × rA`. Aplicar `jn·n + jt·t` no
 * ponto P muda velocidade linear E angular simultaneamente — bater na quina
 * gera spin (lever arm tangencial não-zero), bater face-a-face só inverte
 * direção (lever arm paralelo a `n`), e a fricção tangencial acopla
 * sliding ↔ spin: quadrado raspando numa parede converte parte da
 * velocidade tangencial em rotação (rolling), e quadrados se atritam ao
 * encostarem.
 *
 * O ponto de contato é aproximado por **canto mais avançado**: dos 4 cantos
 * world-space do quadrado, escolhemos o que tem menor projeção na normal
 * (= mais penetrado). Empates dentro de um epsilon (caso face-a-face) são
 * promediados, virando o midpoint da face. Resultado:
 *
 *  - hit face-a-face: dois cantos empatam → midpoint da face → `rA` paralelo
 *    a `-n` → `rA × n = 0` → zero spin induzido (correto).
 *  - hit canto-em-face: um canto ganha → `rA` com componente tangencial →
 *    spin induzido proporcional à perpendicularidade entre `rA` e `n`.
 *
 * `collision.point` hoje devolve o centro do OBB, não o ponto geométrico —
 * por isso a aproximação local. Refinar `SweepResult.point` é trabalho de
 * uma change futura.
 *
 * O sweep só fica rotacionado quando `transform.rotation != 0f`, então cada
 * pair routes através de `sweepRotatedRectRotatedRect`.
 */
private const val SQUARE_COUNT = 16
private const val SQUARE_SIZE = 24f
private const val WALL_THICKNESS = 10f

// mass = 1, momento de inércia uniforme de um quadrado: I = m·(w² + h²)/12.
// Para w == h == SIZE: I = SIZE²/6.
private const val SQUARE_INERTIA = SQUARE_SIZE * SQUARE_SIZE / 6f

// Coeficiente de fricção de Coulomb no contato. Cap do impulso tangencial:
// |j_t| <= MU · |j_n|. ~0.4 é típico de plástico/madeira; baixo → desliza,
// alto → "agarra" e rola.
private const val MU = 0.4f
private const val FRICTION_EPS = 1e-3f

@Serializable
class TumblingSwarmDemo : Node2D() {

    @Transient
    private val rng = Random(0xDEADBEEF7L)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "TumblingSwarmDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        addChild(makeWall(Vec2(-WALL_THICKNESS, -WALL_THICKNESS), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "topWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, h), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "bottomWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "leftWall" })
        addChild(makeWall(Vec2(w, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "rightWall" })
        val padding = SQUARE_SIZE
        repeat(SQUARE_COUNT) { i ->
            val px = padding + rng.nextFloat() * (w - 2f * padding)
            val py = padding + rng.nextFloat() * (h - 2f * padding)
            val speed = 90f + rng.nextFloat() * 90f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val localRotation = rng.nextFloat() * 2f * Math.PI.toFloat()
            // Initial angular velocity in ±2 rad/s — high enough that
            // contacts visibly transfer spin between squares.
            val angularVel = (rng.nextFloat() - 0.5f) * 4f
            addChild(
                TumblingSquare(
                    color = hue(i.toFloat() / SQUARE_COUNT),
                    initPos = Vec2(px, py),
                    initVx = cos(angle) * speed,
                    initVy = sin(angle) * speed,
                    initRotation = localRotation,
                    initAngularVel = angularVel,
                ).apply { name = "TumblingSquare$i" }
            )
        }
    }

    private fun makeWall(position: Vec2, size: Vec2): StaticBody2D {
        val body = StaticBody2D().apply { transform = Transform(position = position) }
        body.addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { this.size = size }
            }
        )
        return body
    }

    override fun onProcess(dt: Float) {
        if (dt > 0f) instantFps = 1f / dt
    }

    override fun onDraw(renderer: Renderer) {
        val text = "tumbling squares: $SQUARE_COUNT | fps: ${instantFps.roundToInt()}"
        val sceneW = tree?.width ?: 800f
        val textW = renderer.measureText(text, 14f).x
        renderer.drawText(text, Vec2(sceneW - textW - 8f, 18f), size = 14f, color = Color.WHITE)
    }

    private fun hue(h: Float): Color {
        val i = (h * 6f).toInt()
        val f = h * 6f - i
        return when (i % 6) {
            0 -> Color(1f, f, 0f)
            1 -> Color(1f - f, 1f, 0f)
            2 -> Color(0f, 1f, f)
            3 -> Color(0f, 1f - f, 1f)
            4 -> Color(f, 0f, 1f)
            else -> Color(1f, 0f, 1f - f)
        }
    }
}

class TumblingSquare(
    color: Color,
    initPos: Vec2,
    initVx: Float,
    initVy: Float,
    initRotation: Float,
    initAngularVel: Float,
) : CharacterBody2D() {

    @Transient
    internal var vx: Float = initVx

    @Transient
    internal var vy: Float = initVy

    @Transient
    internal var angularVel: Float = initAngularVel

    @Transient
    private val fillColor: Color = color

    init {
        transform = Transform(position = initPos, rotation = initRotation)
        addChild(
            CollisionShape2D().apply {
                // Center the rect on the body's position so rotation pivots
                // around the geometric center, not the top-left corner.
                transform = Transform(position = Vec2(-SQUARE_SIZE / 2f, -SQUARE_SIZE / 2f))
                shape = RectangleShape2D().apply { size = Vec2(SQUARE_SIZE, SQUARE_SIZE) }
            }
        )
    }

    override fun onPhysicsProcess(dt: Float) {
        // Integrate angular velocity into rotation before the sweep so the
        // shape uses the up-to-date orientation. moveAndCollide snapshots
        // rotation at the call's start; with this dt the intra-tick drift
        // is sub-pixel.
        transform = transform.copy(rotation = transform.rotation + angularVel * dt)

        val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
        val n = collision.normal
        val other = collision.collider
        if (other is TumblingSquare) {
            // Identity-hash ordering: only the lower-hash side computes the
            // impulse, mutating both bodies in one go. Avoids double-apply
            // when the other side's onPhysicsProcess triggers the same pair.
            if (System.identityHashCode(this) < System.identityHashCode(other)) {
                resolveSquareSquare(this, other, n)
            }
        } else {
            resolveSquareWall(this, n)
        }
    }

    override fun onDraw(renderer: Renderer) {
        val world = world()
        val c = cos(world.rotation)
        val s = sin(world.rotation)
        val h = SQUARE_SIZE / 2f
        val locals = listOf(
            Vec2(-h, -h),
            Vec2(h, -h),
            Vec2(h, h),
            Vec2(-h, h),
        )
        val worldPts = locals.map { v ->
            Vec2(v.x * c - v.y * s + world.position.x, v.x * s + v.y * c + world.position.y)
        }
        renderer.drawPolygon(worldPts, fillColor)
    }
}

// Leading point on a rotated square's surface in the `-normal` direction.
// Returns the offset from the square's center to that point (i.e., `rA`
// directly, no center addition). Ties within epsilon are averaged so face-
// vs-face hits collapse to the face midpoint (zero tangential lever arm),
// while vertex hits keep a single corner (non-zero lever arm → spin).
private fun leadingOffset(rotation: Float, halfSize: Float, normal: Vec2): Vec2 {
    val c = cos(rotation)
    val s = sin(rotation)
    val locals = arrayOf(
        Vec2(-halfSize, -halfSize),
        Vec2(halfSize, -halfSize),
        Vec2(halfSize, halfSize),
        Vec2(-halfSize, halfSize),
    )
    val worldOffsets = Array(4) { i ->
        val l = locals[i]
        Vec2(l.x * c - l.y * s, l.x * s + l.y * c)
    }
    var minProj = Float.POSITIVE_INFINITY
    for (off in worldOffsets) {
        val p = off.x * normal.x + off.y * normal.y
        if (p < minProj) minProj = p
    }
    val eps = halfSize * 0.05f
    var sumX = 0f; var sumY = 0f; var count = 0
    for (off in worldOffsets) {
        val p = off.x * normal.x + off.y * normal.y
        if (p - minProj < eps) {
            sumX += off.x
            sumY += off.y
            count++
        }
    }
    return Vec2(sumX / count, sumY / count)
}

// Wall (mass = ∞): only the square accumulates impulse. The lever arm
// `rA` comes from the leading corner of the rotated square, so a quina
// hit produces non-zero `rA × n` and the wall imparts spin onto the body.
// After the normal impulse we also apply a tangential (Coulomb) friction
// impulse — what couples linear sliding and angular spin so a square
// scraping along a wall picks up roll instead of just bouncing.
private fun resolveSquareWall(a: TumblingSquare, n: Vec2) {
    val rA = leadingOffset(a.transform.rotation, SQUARE_SIZE / 2f, n)
    // velocity at contact = vA + ω × rA (2D cross: ω × r = (-ω·ry, ω·rx))
    val vAPx = a.vx - a.angularVel * rA.y
    val vAPy = a.vy + a.angularVel * rA.x
    val velRelN = vAPx * n.x + vAPy * n.y
    if (velRelN >= 0f) return // already separating

    // 2D scalar cross: r × n = rx·ny - ry·nx
    val rAxN = rA.x * n.y - rA.y * n.x
    val denomN = 1f /* 1/mA */ + (rAxN * rAxN) / SQUARE_INERTIA
    val jn = -2f * velRelN / denomN // e = 1 (elastic)
    a.vx += jn * n.x
    a.vy += jn * n.y
    a.angularVel += jn * rAxN / SQUARE_INERTIA

    // Coulomb friction at contact. Tangent unit vector points in the
    // direction A is sliding; apply an opposite impulse that tries to
    // brake the tangential velocity to zero, capped at MU·|jn|.
    val velTangX = vAPx - velRelN * n.x
    val velTangY = vAPy - velRelN * n.y
    val velTangLen = sqrt(velTangX * velTangX + velTangY * velTangY)
    if (velTangLen < FRICTION_EPS) return
    val tx = velTangX / velTangLen
    val ty = velTangY / velTangLen
    val rAxT = rA.x * ty - rA.y * tx
    val denomT = 1f + (rAxT * rAxT) / SQUARE_INERTIA
    val jtBrake = velTangLen / denomT // magnitude that would fully stop sliding
    val jt = min(jtBrake, MU * jn)
    a.vx -= jt * tx
    a.vy -= jt * ty
    a.angularVel -= jt * rAxT / SQUARE_INERTIA
}

// Equal-mass square-vs-square. Contact point is A's leading corner toward
// B (along `-n`); B's lever arm is taken relative to that same world point.
// Glancing hits (corner-into-face) thus generate spin on both bodies.
private fun resolveSquareSquare(a: TumblingSquare, b: TumblingSquare, n: Vec2) {
    val centerA = a.position
    val centerB = b.position
    // A's leading offset toward B is along -n in A's frame.
    val rA = leadingOffset(a.transform.rotation, SQUARE_SIZE / 2f, n)
    // Contact point in world; B's lever arm is the same point measured from
    // B's center. (n points from B toward A, so contact lies near A's surface.)
    val pX = centerA.x + rA.x
    val pY = centerA.y + rA.y
    val rAx = rA.x
    val rAy = rA.y
    val rBx = pX - centerB.x
    val rBy = pY - centerB.y
    val vAPx = a.vx - a.angularVel * rAy
    val vAPy = a.vy + a.angularVel * rAx
    val vBPx = b.vx - b.angularVel * rBy
    val vBPy = b.vy + b.angularVel * rBx
    val velRelX = vAPx - vBPx
    val velRelY = vAPy - vBPy
    val velRelN = velRelX * n.x + velRelY * n.y
    if (velRelN >= 0f) return // separating

    val rAxN = rAx * n.y - rAy * n.x
    val rBxN = rBx * n.y - rBy * n.x
    val denomN = 1f + 1f + (rAxN * rAxN) / SQUARE_INERTIA + (rBxN * rBxN) / SQUARE_INERTIA
    val jn = -2f * velRelN / denomN
    a.vx += jn * n.x
    a.vy += jn * n.y
    b.vx -= jn * n.x
    b.vy -= jn * n.y
    a.angularVel += jn * rAxN / SQUARE_INERTIA
    b.angularVel -= jn * rBxN / SQUARE_INERTIA

    // Coulomb friction at contact between the two squares. Tangent unit
    // vector points in A's sliding direction relative to B; A brakes,
    // B picks up the opposite kick so total tangential momentum is
    // preserved. Both lever arms produce angular reaction.
    val velTangX = velRelX - velRelN * n.x
    val velTangY = velRelY - velRelN * n.y
    val velTangLen = sqrt(velTangX * velTangX + velTangY * velTangY)
    if (velTangLen < FRICTION_EPS) return
    val tx = velTangX / velTangLen
    val ty = velTangY / velTangLen
    val rAxT = rAx * ty - rAy * tx
    val rBxT = rBx * ty - rBy * tx
    val denomT = 1f + 1f + (rAxT * rAxT) / SQUARE_INERTIA + (rBxT * rBxT) / SQUARE_INERTIA
    val jtBrake = velTangLen / denomT
    val jt = min(jtBrake, MU * jn)
    a.vx -= jt * tx
    a.vy -= jt * ty
    b.vx += jt * tx
    b.vy += jt * ty
    a.angularVel -= jt * rAxT / SQUARE_INERTIA
    b.angularVel += jt * rBxT / SQUARE_INERTIA
}
