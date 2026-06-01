package com.neoutils.engine.debug

import com.neoutils.engine.render.Color

// Gizmo/log colors are owned by `DebugTheme` (the single source of debug
// appearance). These top-level aliases stay for call sites that read the
// named constants directly; each resolves to the theme so there is no second
// source of truth to drift.

/** Color used to outline `Area2D` shape bounds (triggers, e.g. goals). */
val DEBUG_AREA_COLOR: Color = DebugTheme.areaColor

/** Color used to outline `PhysicsBody2D` shape bounds (solid bodies). */
val DEBUG_BODY_COLOR: Color = DebugTheme.bodyColor

/** Color of the `VelocityGizmoWidget` arrows (cyan). */
val DEBUG_VELOCITY_COLOR: Color = DebugTheme.velocityColor

/** Color of the `ContactGizmoWidget` markers and normal lines (yellow). */
val DEBUG_CONTACT_COLOR: Color = DebugTheme.contactColor

/** Log overlay text color for `Debug`/`Info` entries (neutral light gray). */
val DEBUG_LOG_NEUTRAL_COLOR: Color = DebugTheme.logNeutralColor

/** Log overlay text color for `Warn` entries (amber). */
val DEBUG_LOG_WARN_COLOR: Color = DebugTheme.logWarnColor

/** Log overlay text color for `Error` entries (red). */
val DEBUG_LOG_ERROR_COLOR: Color = DebugTheme.logErrorColor

/** Color of the `SelectionGizmoWidget` oriented box (magenta). */
val DEBUG_SELECTION_COLOR: Color = DebugTheme.selectionColor
