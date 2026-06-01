package com.neoutils.engine.debug

/**
 * Anchor a [ScreenDebugWidget] declares so the [DebugDock] can place it. The
 * dock stacks every enabled widget sharing a slot from that corner/center
 * inward, so a slot can hold any number of widgets without collision.
 */
enum class DockSlot {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_CENTER,
    BOTTOM_CENTER,
}
