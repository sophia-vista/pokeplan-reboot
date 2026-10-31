# Launcher widget grid (the Smart Launcher style canvas)

Owner's request (31 Oct 2026): launcher widgets with drag and drop and resize, like Smart Launcher. Decision taken: a **grid canvas**
(not stacked full-width slots with a drag bar).

## The model (built, 0.90.8): `launcher/WidgetGrid.kt`
- 4 columns; a row is as tall as a column is wide; rows grow downwards. Two pages: `HOME` (over the town) and `TODAY`.
- `GridWidget(page, kind, arg, col, row, w, h)`; identity is page + kind + arg. Limits per kind in `WidgetKind.limits()`
  (min, max and starting size in cells).
- Rules (`WidgetGrid`, pure and tested): `add` (first free spot), `move` (what is in the way goes down, cascading, never
  across pages), `resize` (between the limits, shifts left to stay on the grid), `remove`, `rows`.
- `fromSlots`: the old stacked layout becomes a grid with no change in look: same order, full width, Small/Medium/Large
  become heights, the home page's bottom widgets end at the foot.
- Saved as `X page kind arg col row w h` lines in `LauncherPrefs` (`grid`, null until first changed; `gridWidgets` shows the
  old `layout` as a grid meanwhile). A damaged line is skipped. The old `G` lines are still read and written.

## Built (0.91.0): the grid is drawn
`launcher/GridPage.kt` draws `gridWidgets` in cells (4 columns, rows of `WidgetGrid.ROW_DP` = 40 dp, so the old stack of about 17 rows fits one screen), each widget's body clipped to its cells and told its old size from its height (`asSlot`). Behind "Widget grid (preview)" in Launcher settings (`LauncherPrefs.gridMode`, off). The old widget bodies are not yet adapted to narrow or short cells (they clip).

## Still to build (in this order)
1. **Adapt the bodies:** a half-width widget shows one column of its content; a short one fewer rows; then make the grid the default.
2. **Edit mode like Smart Launcher:** long-press a widget to lift it (a little scale and shadow), drag it anywhere (a ghost
   of the drop cell, widgets bump down live), drop; a frame with corner handles to resize in whole cells with the limits as
   the stops; a remove (x) and the widget's settings in the frame; drag to the screen edge for the other page.
3. **Retire** the old edit bar and `layout` once the grid has shipped for a version; keep decoding `G` lines.
4. Accessibility: every drag has a button alternative (move up, down, left, right, wider, narrower, taller, shorter), and
   Reduce motion drops the lift animation.
5. Android widgets (`ANDROID`): the host's own min and max cell sizes feed `limits()` for that widget.
