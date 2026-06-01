# debug-ui-shell Specification

## Purpose

Defines the shared chrome and layout shell for the debug subsystem: a single
`DebugTheme` as the one source of panel appearance, and a per-`SceneTree`
`DebugDock` that positions each `ScreenDebugWidget` by a declared `DockSlot`
(corner or center). Widgets report the size they occupy and draw from the
origin the dock hands them, so no debug widget hardcodes a screen corner or
its own panel colors.

## Requirements

### Requirement: Single debug theme

The debug subsystem SHALL expose a `DebugTheme` as the single source of panel
appearance (background color, border color and thickness, margins, paddings,
and a named text scale), and every debug panel SHALL derive its chrome from it.

#### Scenario: Panels share the same chrome

- **WHEN** two distinct debug panels are drawn
- **THEN** both use the same background color, border, and margins from `DebugTheme`

#### Scenario: Gizmo/log colors come from the theme

- **WHEN** a gizmo or the log overlay needs a color
- **THEN** the color is resolved from the single theme source (not a local literal)

### Requirement: Corner-slot layout

The debug subsystem SHALL expose a per-tree `DebugDock` that positions each
`ScreenDebugWidget` by a declared `DockSlot` (corner or center), stacking
widgets of the same slot vertically from the edge, with a gutter from the theme.

#### Scenario: Widgets in the same slot stack without overlapping

- **WHEN** two `ScreenDebugWidget`s declare the same `DockSlot` and are enabled
- **THEN** the dock positions them stacked vertically, without overlap

#### Scenario: Layout re-flows on resize

- **WHEN** `tree.size` changes
- **THEN** the dock recomputes each widget's origin, keeping them inside the
  viewport and anchored to their corner

#### Scenario: A new widget declares a slot, not pixels

- **WHEN** a new `ScreenDebugWidget` is registered with a `DockSlot`
- **THEN** it appears stacked in that slot with no hardcoded pixels

### Requirement: Widget reports size; dock gives the origin

Each `ScreenDebugWidget` SHALL report the size it occupies (measured from its
content) and SHALL draw from the origin provided by the `DebugDock`, without
positioning itself by its own absolute pixels.

#### Scenario: A variable-height panel re-stacks

- **WHEN** a variable-height widget (e.g. picker, log) changes size
- **THEN** the dock re-stacks the following widgets in the slot from the new size

#### Scenario: No hardcoded origin of its own

- **WHEN** a `ScreenDebugWidget` is drawn
- **THEN** its position comes from the dock, not from an internal corner constant
