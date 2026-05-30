package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2

/** A single resolved contact captured during `PhysicsSystem.step`. */
data class ContactRecord(val point: Vec2, val normal: Vec2)

/**
 * Per-`SceneTree` buffer of the contacts the impulse solver resolved during
 * the most recent physics step. Runtime-only (never `@Serializable`, never
 * shared across trees), reached by `PhysicsSystem.step` via `tree.debug` and
 * read by `ContactGizmoWidget.drawDebug` in the same frame.
 *
 * [recording] gates the capture: it mirrors `ContactGizmoWidget.enabled`.
 * When `false`, the step records nothing and pays no per-contact cost; when
 * `true`, the step clears the buffer at the start and appends one
 * [ContactRecord] per resolved contact.
 *
 * Kinematic contacts resolved by `CharacterBody2D.moveAndCollide` happen in
 * `_physics_process`, **before** `PhysicsSystem.step` clears the buffer. They
 * are held in a separate [staged] area until the following `step` folds them
 * into [records] via [takeStaged] (after the start-of-step [clear]), so they
 * survive into the same frame's render alongside the rigid contacts.
 */
class PhysicsContactBuffer {

    /** Driven by `ContactGizmoWidget.enabled`; consulted by `PhysicsSystem.step`. */
    var recording: Boolean = false

    private val _records: MutableList<ContactRecord> = mutableListOf()

    val records: List<ContactRecord> get() = _records

    private val _staged: MutableList<ContactRecord> = mutableListOf()

    val staged: List<ContactRecord> get() = _staged

    fun clear() {
        _records.clear()
    }

    fun append(point: Vec2, normal: Vec2) {
        _records += ContactRecord(point, normal)
    }

    /** Holds a kinematic contact until the next `step` consolidates it. */
    fun stage(point: Vec2, normal: Vec2) {
        _staged += ContactRecord(point, normal)
    }

    /** Moves every staged contact into [records] and empties the staging area. */
    fun takeStaged() {
        _records += _staged
        _staged.clear()
    }
}
