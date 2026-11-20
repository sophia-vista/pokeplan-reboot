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
`launcher/GridPage.kt` draws `gridWidgets` in cells (4 columns, rows of `WidgetGrid.ROW_DP` = 36 dp, so the old stack of about 17 rows fits one screen), each widget's body clipped to its cells and told its old size from its height (`asSlot`). Behind "Widget grid (preview)" in Launcher settings (`LauncherPrefs.gridMode`, off). The old widget bodies are not yet adapted to narrow or short cells (they clip).

## Built (0.92.0): edit mode on the grid
Long-press a widget (`GridCanvas`): frames, drag to move (snaps on release, a yellow frame shows the landing cell), a corner square to resize (`WidgetGrid.droppedAt`, `resizedTo`), X to remove, accessibility actions for every gesture. `LauncherPrefs.addWidget`/`removeWidget`/`removeGridWidget` keep the grid and the old layout together. `ScreenshotTest.launcherGridEdit` drags and resizes with real pointer events.

## Built (0.93.0): the grid is the default
Bodies adapt through `LocalCells` (clock, shortcuts, app buttons) and `LocalGridFill` (the frame fills its cells, content is clipped inside); `LauncherPrefs.gridMode` is true by default and the Launcher settings switch returns to the list.

## Built (0.94.0): pages and live preview
`WidgetPage` is HOME, TODAY and PAGE3 to PAGE5; `LauncherPrefs.pages` (saved `K pages`) says how many exist. `GridCanvas` previews every drag and resize live (the others slide), a dragged widget held at a screen edge hops pages (`moveGridWidgetToPage`, which makes the page when needed), the edit bar has New page and Delete this page. `LauncherScreen` slides any number of pages.

## Since 0.94.0
Pages became ordered (`LauncherPrefs.pageOrder`, saved `K pageOrder`): the edit bar adds a page at the left or right end ("Left+", "Right+": `GridEditBar` in `GridPage.kt`, `LauncherPrefs.addPage`) and deletes a page, moving its widgets to the next one (`deletePage`). The other apps' widgets take their limits from the provider (`HostedWidgetSpec.limits` in `HostedWidgets.kt`, fed by `LauncherActivity`). Every drag has a button alternative: the frame's accessibility actions are Move up, down, left and right, Wider, Narrower, Taller and Shorter (`GridPage.kt`), and Reduce motion makes moves snap instead of slide.

## Still open
- **Retire** the old list mode (`LauncherPrefs.layout`, the `G` lines, the per-widget `EditBar` in `LauncherWidgets.kt` and the Launcher settings switch back to the list) once the grid has shipped for a version; keep decoding `G` lines. The grid is the default but the list mode is still in the code.

Everything else on the earlier to-do list (adapt the bodies, the Smart Launcher style edit mode, the accessibility alternatives, the hosted widgets' limits) is built, as above.
