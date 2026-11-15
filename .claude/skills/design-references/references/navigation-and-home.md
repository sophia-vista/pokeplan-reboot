# Reference: navigation (navbar.gallery) and home and onboarding (supahero.io)

## Navigation (navbar.gallery)
Seen: categories Static, Dropdown, Mega Menu, Side Bar, Search Bar, Announcement, Full Screen, Breadcrumbs. Examples:
Chesapeake Plywood (a dark static bar with dropdown carets), Velt (a dark mega menu with icons in three columns),
Clonix (a dark full-screen menu of large words), Aveon (a cream side panel of plain stacked words beside a
full-height photo), Orchid (a floating pill nav over a blurred colour background).

**Take this**
- Static: the four bottom tabs, each an icon and a label, the selected one filled (DESIGN_SYSTEM.md 5.5). Nothing nested.
- Clonix's full-screen list of large words is close to our section menus: `MenuGrid`'s big cards with real item
  icons, one tap to a section, Back to return.
- Breadcrumbs, made small: `SectionScaffold`'s title and Back, and the system back gesture, always.
- Side Bar: on wide desktop and web windows (dev conveniences only; Android is the product) a labelled side rail can
  stand in for the bottom tabs (same four sections, same order). Phones keep the bottom tabs.
- Announcement: one slim, dismissible line for something that really changed (What's new, tomorrow's spotlight
  type). One at a time, never stacked.
- Search Bar: only where lists get long (Planner All, Pokédex), at the top of that list, never in the tab bar.

**Not that**
- Dropdown carets, mega menus, hamburgers, floating pill navs, a filled "Sign in" button in a header.

## Home and onboarding (supahero.io)
Seen: Dream Design Laboratory (a full-bleed photo of plants against glass, small nav, almost no text), Lando Norris
(a large portrait cut-out), Treize grammes (a serif headline in its own column beside a 3D toggle), Dribbble (a centred
headline, one line and one button), Better Stack (dark glow, email field, logo strip), Anubi and eddie (3D), Red
Antler and KARO (huge type).

**Take this**
- The picture carries the screen, with very little text (Dream Design Laboratory, Lando Norris). Ours: the buddy,
  large and animated in its scene (`BuddyStage`), level and progress small in a corner.
- Text in its own space beside or under the picture, never over it (Treize grammes). Over a scene, use a panel
  (`TextBox`) rather than bare text.
- One headline, one line, one button (Dribbble). Ours: the title screen and each onboarding step: one question,
  sensible default already chosen, one PixelButton.

**Not that**
- 3D, product screenshots, rotating words, autoplay video, dark glow, an email field on the first screen (the app
  has no accounts).

