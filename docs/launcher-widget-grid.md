# Launcher widget grid (the Smart Launcher style canvas)

A grid canvas (not stacked full-width slots with a drag bar): drag, drop and resize, like Smart Launcher. It is the default; the model is `launcher/WidgetGrid.kt`, the canvas `launcher/GridPage.kt`.

## Rules
- 4 columns; a row is as tall as a column is wide in the model, drawn 36 dp high (`WidgetGrid.ROW_DP`). Rows grow downwards.
- Pages are ordered (`LauncherPrefs.pageOrder`, saved `K pageOrder`): `WidgetPage` is HOME (over the town), TODAY and PAGE3 to PAGE5; `LauncherPrefs.pages` (saved `K pages`) says how many exist. `LauncherScreen` slides any number of pages.
- `GridWidget(page, kind, arg, col, row, w, h)`; identity is page + kind + arg. Limits per kind in `WidgetKind.limits()` (min, max and starting size in cells); the other apps' widgets take theirs from the provider (`HostedWidgetSpec.limits` in `HostedWidgets.kt`).
- `WidgetGrid` (pure, tested): `add` (first free spot), `move` (what is in the way goes down, cascading, never across pages), `resize` (between the limits, shifts left to stay on the grid), `remove`, `rows`, `droppedAt`, `resizedTo`.
- Saved as `X page kind arg col row w h` lines in `LauncherPrefs` (`grid`; null until first changed, `gridWidgets` shows the old `layout` as a grid meanwhile). A damaged line is skipped; the old `G` lines are still read and written. `fromSlots` turns the old stacked layout into a grid with the same look.
- Edit mode (`GridCanvas`): long-press a widget; frames, drag to move (live preview, the others slide; a yellow frame shows the landing cell), a corner square to resize, X to remove. A dragged widget held at a screen edge hops pages (`moveGridWidgetToPage`). The edit bar (`GridEditBar`) adds a page at the left or right end ("Left+", "Right+") and deletes one, moving its widgets to the next (`deletePage`).
- Bodies adapt to their cells through `LocalCells` and `LocalGridFill` (the frame fills its cells, content is clipped inside).
- Accessibility: every drag has a button alternative (Move up, down, left and right, Wider, Narrower, Taller, Shorter); Reduce motion makes moves snap instead of slide.
- `ScreenshotTest.launcherGridEdit` drags and resizes with real pointer events.

## Still open
- **Retire** the old list mode (`LauncherPrefs.layout`, the `G` lines, the per-widget `EditBar` in `LauncherWidgets.kt`, `LauncherPrefs.gridMode` and the Launcher settings switch back to the list); keep decoding `G` lines.
