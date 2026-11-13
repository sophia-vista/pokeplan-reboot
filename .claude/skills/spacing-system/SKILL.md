---
name: spacing-system
description: The spacing scale and rules for PokéLife screens (`Spacing` in `PixelTheme.kt`): inset, stack, inline and density. Use before laying out or restyling a screen, card, list, dialogue box or widget in composeApp or androidApp, adding a spacing value, or reviewing a layout for cramped or uneven gaps.
---

# Spacing System

## The scale
The scale lives in `composeApp/.../theme/PixelTheme.kt`, object `Spacing` (a 4 dp base, in dp). Use its names, never a bare number:

| Spacing | dp | Use it for |
|---|---|---|
| `XS` | 4 | between an icon and its label, chips in a row |
| `S` | 8 | related items in a stack or row, the gap between a title and its caption |
| `M` | 12 | a card's stack of lines, the default `Arrangement.spacedBy` between cards |
| `L` | 16 | inset of a plain card, space above a group |
| `XL` | 20 | `Page` side padding |
| `XXL`, `Section` | 24 | between distinct sections |
| `XXXL` | 32 | above a screen's first section, an empty state's breathing room |
| `Target` | 48 | the smallest touch target |

- A dialogue box (`TextBox`) is padded `BoxX` (24) by `BoxY` (18): its 8 dp frame plus a gap. Anything that draws a box of its own (the World tile box, the launcher's `PmdBox`) keeps the same gap round its text; the owner has twice said the padding inside such a box was too little.
- A picture inside a dialogue frame is inset by the frame's thickness and given a dark fill behind it, so a pixel the picture misses shows dark, never white.
- In the launcher's pixel text, space in the art's own pixels (`pixel`, then divide by density) so a gap stays a whole number of art pixels.
- Density: the launcher's and Pokégear's dense lists drop one step (`M` to `S`); reading screens (a story, an About page) rise one. Never mix steps inside one component.
- Touch targets stay `Target` tall however compact the row looks; pad the hit area, not the picture.
- Check at the narrow phone width (360 dp), the wide phone width, and with the system font at 130%: gaps that hold at 100% can collapse when the text grows.
