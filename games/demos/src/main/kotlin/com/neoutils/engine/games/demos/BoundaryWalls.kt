package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Constrói uma `StaticBody2D` com um único `CollisionShape2D + RectangleShape2D`
 * no `position` e `size` informados. Utilitário canônico para demos sem
 * `Camera2D` que precisam de paredes estáticas. Demos cuja parede deve viver
 * em frame local de um wrapper rotativo (caso de `RotatingBoxDemo`) usam este
 * helper direto; demos cuja fronteira deve acompanhar `tree.size` usam
 * [BoundaryWalls], que internamente também recorre a `makeStaticWall`.
 */
internal fun makeStaticWall(position: Vec2, size: Vec2): StaticBody2D {
    val body = StaticBody2D().apply { transform = Transform(position = position) }
    body.addChild(
        CollisionShape2D().apply {
            shape = RectangleShape2D().apply { this.size = size }
        }
    )
    return body
}

/**
 * **Arena container** de 4 `StaticBody2D` (top/bottom/left/right) que mantém o
 * perímetro alinhado a `(0, 0)..(tree.width, tree.height)` em tempo real.
 *
 * Existe para demos sem `Camera2D` cujo "mundo" é literalmente o retângulo
 * da janela: ao redimensionar, as paredes precisam acompanhar para evitar
 * bolinhas escapando ou batendo em barreiras invisíveis. Atualiza por
 * polling em `onPhysicsProcess` — early-return quando `tree.size == lastSize`,
 * para que demos sem resize ativo paguem apenas uma comparação de `Vec2` por
 * frame de física.
 *
 * **Padrão de uso**: atores físicos que devem colidir com o perímetro são
 * adicionados como filhos diretos da instância de `BoundaryWalls`, não como
 * siblings dela no nó do demo:
 *
 * ```
 * val arena = BoundaryWalls().also { addChild(it) }
 * repeat(N) { arena.addChild(Ball(...)) }
 * ```
 *
 * O motivo é estrutural: `CharacterBody2D.moveAndCollide` só considera bodies
 * cujo `parent` coincide com o `parent` do corpo se movendo. Para o sweep
 * encontrar as 4 paredes, os atores precisam viver no mesmo parent frame que
 * elas — ou seja, dentro da própria `BoundaryWalls`.
 *
 * Demos com paredes em frame local que **não** devem reagir a `tree.size`
 * (caso do demo `5 Rotating box`, onde as paredes giram solidárias ao wrapper)
 * devem usar [makeStaticWall] direto em vez deste wrapper.
 */
@Serializable
class BoundaryWalls(private val thickness: Float = 10f) : Node2D() {

    @Transient
    private var lastSize: Vec2 = Vec2.ZERO

    init {
        name = "BoundaryWalls"
        if (children.isEmpty()) {
            addChild(makeStaticWall(Vec2.ZERO, Vec2.ZERO).apply { name = "topWall" })
            addChild(makeStaticWall(Vec2.ZERO, Vec2.ZERO).apply { name = "bottomWall" })
            addChild(makeStaticWall(Vec2.ZERO, Vec2.ZERO).apply { name = "leftWall" })
            addChild(makeStaticWall(Vec2.ZERO, Vec2.ZERO).apply { name = "rightWall" })
        }
    }

    override fun onPhysicsProcess(dt: Float) {
        val current = tree?.size ?: return
        if (current == lastSize) return
        relayout(current.x, current.y)
        lastSize = current
    }

    private fun relayout(w: Float, h: Float) {
        place("topWall", Vec2(-thickness, -thickness), Vec2(w + 2f * thickness, thickness))
        place("bottomWall", Vec2(-thickness, h), Vec2(w + 2f * thickness, thickness))
        place("leftWall", Vec2(-thickness, 0f), Vec2(thickness, h))
        place("rightWall", Vec2(w, 0f), Vec2(thickness, h))
    }

    private fun place(name: String, position: Vec2, size: Vec2) {
        val body = findChild(name) as StaticBody2D
        body.transform = Transform(position = position)
        val collisionShape = body.children.first { it is CollisionShape2D } as CollisionShape2D
        (collisionShape.shape as RectangleShape2D).size = size
    }
}
