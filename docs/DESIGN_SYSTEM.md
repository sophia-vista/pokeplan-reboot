# Design system

> **Superseded by `docs/pokelife/FEATURES/DESIGN_SYSTEM.md`** (the owner's design rules of 8 November 2026: nothing is round, every control is pixel-drawn). Where this file disagrees (card radii, pills, `Spacing.CardRadius`, which no longer exists), that one wins. Kept as the history of the 0.64.0 audit.

The rules the screens follow, so a change is made once. The tokens live in code (`composeApp/.../ui/theme/PixelTheme.kt`,
`Widgets.kt`); the reasoning is in `docs/ui-ux-brief.md` and `docs/laws-of-ux-review.md`. Update this file in the same
change as any token, component or rule it describes. Approved in the 0.64.0 design audit.

## 1. Two registers
- **Utility** (the app: every screen under the four tabs): cards drawn as dialogue boxes: `Pixel.Panel` at 80 % (`CARD_OPACITY`) inside a thin version of the
  game's frame (`Modifier.dialogueBox()`: hairline, band 2 px at the sides and 1 at the top and bottom, a muted inner line, 2 dp art
  pixels, 6 dp corners), no shadow, no padding added (the card's own padding keeps content clear: `Spacing.BoxX` 24 dp sideways and `BoxY` 18 dp above and below, so text sits about 9 art pixels inside the frame). Components: `TextBox`, `ListRow`,
  `MenuGrid`, `PixelButton`.
- **World** (the Android launcher home only): the game's dialogue box: a see-through black panel (`PmdPalette.Fill`, about 65 %) in a rounded frame of
  a pale hairline (`#A8C0E0`), a blue band (`#6080F8`, 1 px at the top and bottom, 5 px at the sides) and a deep line (`#4820F8`), drawn at
  the town's pixel scale in the dialogue font. Components: `PmdBox`, `PmdMenuLine`, `PmdButton`, `PmdRule`.
- Both registers share one frame (`theme/DialogueFrame.kt`). The world register adds the see-through black fill, the thick side band and the
  dialogue font; the utility register keeps the readable fonts and the page colours. A screen belongs to one register.

## 2. Colour (`Pixel`)
| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| Paper / Panel / Shade / Border | `#F7F5F0` / `#FFFFFF` / `#ECECF2` / `#E1E1E8` | `#14151F` / `#1F2132` / `#2B2E44` / `#3A3E58` | page, surfaces, empty tracks, lines |
| Ink / InkSoft / OnInk | `#252637` / `#666B7E` / white | `#ECEDF5` / `#A9AEC4` / `#14151F` | text |
| Red | `#D23B2E` | `#EC4C3E` | brand, the one main action, headers. White text on it is 4.8:1 (AA) |
| Blue | `#2C6CCC` | `#4A89E8` | selection, focus, links |
| Green / Warning / Danger | `#1E7F52` / `#946000` / `#B42332` | `#2E9E6A` / `#D99A1E` / `#EC6A75` | success and a running session / caution / data at risk only |
| Night, Scrim | `#101C3C`, black | same | the tint over a scene at night; under text on a picture (with an alpha) |
| Violet, Pink, Teal, Amber | `#8B5CF6`, `#EC4899`, `#0D9488`, `#F59E0B` | same | identity colours for categories and regions, never for state |
- Each module has a frame colour (`LocalFrameTone`, `frameToneOf(colour)`): Plan red, Care green, menu cards their own tint, everything else the game's blue. It never replaces state colours.
- Colour means one thing: red = the main action, green = done or running, blue = selected or focused. Never decoration.
- No colour literal outside `theme/`, the day palette and `PmdPalette`.
- Text contrast is at least 4.5:1 (3:1 only for text of 24 sp or bold 18.66 sp and up).

## 3. Spacing (`Spacing`)
- Allowed values: 1, 2 (hairlines), 4 `XS`, 8 `S`, 12 `M`, 16 `L`, 20 `XL`/`Page`, 24 `XXL`, 32 `XXXL` dp. **No 6 dp or 10 dp.**
- Touch targets: `Spacing.Target` = 48 dp. No smaller control, and no literal 44 dp.
- Icon sizes: `Icon.S` 24, `Icon.M` 36, `Icon.L` 56, `Icon.XL` 72 dp. Sprites and portraits are sized per screen.

## 4. Typography
- Four sizes: 24 (screen titles), 18 (section titles, buttons), 16 (body), 14 (secondary, labels).
- One typeface family: the Pokémon DS-era font (Pokemon DP, regular and bold) for everything, with the dialogue bitmap font (below) for
  titles, labels and buttons. There is no other font.
- `titleMedium`, `titleSmall` and `labelLarge` are the same style by design; use `labelLarge` for button labels, `title*` for headings.
- Titles, labels and buttons in the utility register use the dialogue font too (`PixelTitle`, `PixelLabel`: dark on light, white on dark and on coloured faces; a text it cannot draw falls back to the pixel font); body text, descriptions and user text are in Pokemon DP. Scale: `appPixel()` (about 1.75 dp per art pixel, 1.25 dp for tab labels).
- The dialogue font (`PmdText`) is the only font of the world register: every line in a dialogue box uses it (long text is cut with "...", marks it lacks become look-alikes or `?`). It is not used in the utility register.
- World register spacing: text starts 9 art pixels inside the fill (3 for the box's own inset, 6 for the line, where the cursor goes); rules and buttons run to the 3.

## 5. Shape and elevation
- Dialogue boxes `DialogueShape` (6 dp, matching the frame's stair-stepped corners); header bands and other surfaces 16 dp (`Spacing.CardRadius`), controls 12 dp, pills fully round. No shadows.

## 6. Components and states
Every interactive component has: default, pressed, focused, disabled.
- **Pressed:** `PixelButton` sinks 2 dp while held (it never animates); other controls use the platform ripple.
- **Focused:** `Modifier.focusRing(shape)` draws a 2 dp `Pixel.Blue` ring (white in the world register). Put it before the `clickable`.
- **Disabled:** `Pixel.Shade` face, `Pixel.InkSoft` label.
- **Selected:** a filled `Pixel.Blue` chip with white text (`TabButton`), or a blue-bordered row with a radio dot (`ChoiceRow`); never black, never red.
- **Empty:** `EmptyState(title, text, icon)`: one real item icon, one calm sentence, the first action. Never a bare blank page.
- **Notice:** `Notice(text, tone)` (Info, Warning, Danger): one calm sentence saying what happened and what to do, announced politely by screen readers; Danger only where data could be lost (docs/design.md, No shame). Buttons that answer it sit under it.
- **Loading:** no spinners. Scenes and towns fade in over 150 ms (`rememberFadeIn`; instant under Reduce motion); a quiet colour or disc stands in meanwhile.
- **MenuGrid:** two columns; a lone last card spans the full width.

## 7. Icons
Real item sprites only (assets/items), one per destination, never the same sprite for two destinations. A section's icon is at the start
of its header; when the header has Back, the icon moves to the end. Assignments: Home = Town Map (`town-map`), Modules = Explorer Kit
(`explorer-kit`), Pokémon = Poké Ball (`poke-ball`), Me = Member Card (`member-card`); inside Pokémon: Party = Premier Ball, PC boxes =
Storage Key, Pokédex = Great Ball, Bag = Seal Bag, Guardians = Master Ball.

## 8. Layout and breakpoints
- Compact (< 600 dp): one column, tab bar at the bottom.
- Medium (600 to 839 dp): the page is a column at most 640 dp wide, centred; tab bar at the bottom.
- Expanded (840 dp and up): the same centred column plus a 96 dp side rail with the four tabs.
- Text scales to the system size; at 1.3x and up a button never sits beside text that no longer fits (it goes underneath).

## 9. Motion
The dark page is a smooth gradient, not the dithered grain of the light one.
Purposeful only: screen changes, a reward, a buddy reaction. Everything stops under Reduce motion. Anything that loops pauses when the
screen is off or the app is in the background.

## 10. Accessibility
Every control has a role and a label (bitmap text carries its text as a content description); focus is visible; contrast as above;
nothing important is behind a gesture alone, and a gesture has a visible alternative.
