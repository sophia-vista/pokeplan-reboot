---
name: spacing-system
description: Create a consistent spacing system based on a base unit with contextual application rules. Use before laying out or restyling a screen, card, list, dialogue box or widget in composeApp or androidApp, adding a spacing value, or reviewing a layout for cramped or uneven gaps.
---

# Spacing System

You are an expert in creating systematic spacing for consistent, harmonious interfaces.

## What You Do
You create spacing systems that bring consistency and rhythm to layouts.

## Base Unit
Choose a base unit (typically 4px or 8px) and build a scale:

| Token | Value |
|---|---|
| 2xs | 2px |
| xs | 4px |
| sm | 8px |
| md | 16px |
| lg | 24px |
| xl | 32px |
| 2xl | 48px |
| 3xl | 64px |

## Spacing Types
- **Inset:** padding inside containers (equal or squish/stretch variants)
- **Stack:** vertical space between stacked elements
- **Inline:** horizontal space between inline elements
- **Grid gap:** space between grid/flex items

## Application Rules
- Related items: smaller spacing (sm/md)
- Distinct sections: larger spacing (lg/xl)
- Page margins: consistent per breakpoint
- Component internal: defined per component

## Density Modes
- **Compact:** reduce spacing by one step (for data-heavy views)
- **Comfortable:** default spacing
- **Spacious:** increase spacing by one step (for reading-focused)

## Best Practices
- Always use the scale: never arbitrary values
- Consistent spacing within components
- Larger gaps between unrelated groups
- Document spacing intent, not just values
- Test spacing at different viewport sizes

## In PokéLife
The scale lives in `composeApp/.../theme/PixelTheme.kt`, object `Spacing` (a 4 dp base, in dp). Use its names, never a bare number:

| Spacing | dp | The scale's step | Use it for |
|---|---|---|---|
| `XS` | 4 | xs | between an icon and its label, chips in a row |
| `S` | 8 | sm | related items in a stack or row, the gap between a title and its caption |
| `M` | 12 | between sm and md | a card's stack of lines, the default `Arrangement.spacedBy` between cards |
| `L` | 16 | md | inset of a plain card, space above a group |
| `XL` | 20 | between md and lg | `Page` side padding |
| `XXL`, `Section` | 24 | lg | between distinct sections |
| `XXXL` | 32 | xl | above a screen's first section, an empty state's breathing room |
| `Target` | 48 | 2xl | the smallest touch target |

- A dialogue box (`TextBox`) is padded `BoxX` (24) by `BoxY` (18): its 8 dp frame plus a gap. Anything that draws a box of its own (the World tile box, the launcher's `PmdBox`) keeps the same gap round its text; the owner has twice said the padding inside such a box was too little.
- A picture inside a dialogue frame is inset by the frame's thickness and given a dark fill behind it, so a pixel the picture misses shows dark, never white.
- In the launcher's pixel text, space in the art's own pixels (`pixel`, then divide by density) so a gap stays a whole number of art pixels.
- Density: the launcher's and Pokégear's dense lists drop one step (`M` to `S`); reading screens (a story, an About page) rise one. Never mix steps inside one component.
- Touch targets stay `Target` tall however compact the row looks; pad the hit area, not the picture.
- Check at the narrow phone width (360 dp), the wide phone width, and with the system font at 130%: gaps that hold at 100% can collapse when the text grows.
