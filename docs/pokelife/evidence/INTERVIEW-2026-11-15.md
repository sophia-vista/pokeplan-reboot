> **Filed as evidence (15 November 2026).** The owner's own record of how they use PokéLife, kept as received; it is evidence, not authority (Product Bible section 10). It was written before the 13 November rename was carried through, so **"HOME" here means BASE**. What it decided is in the specifications and `../IMPLEMENTATION_BACKLOG.md`.

# PokéLife --- Personal Use Interview Synthesis

**Date:** 15 November 2026\
**Purpose:** Consolidate the user's answers about how they currently use
PokéLife, what they value, what prevents adoption, and what should guide
future UX and product decisions.

> This document records interview feedback. It is not a code audit,
> implementation-status report, or usability test. Statements about
> current behavior reflect the user's reported experience;
> recommendations are interpretations derived from that feedback.

------------------------------------------------------------------------

## 1. Product-level findings

### Why PokéLife matters

Pokémon integration is a major reason the user uses PokéLife. The appeal
is not merely cosmetic:

-   The user wants the app to feel like **Pokémon Mystery Dungeon came
    to life on their home screen**.
-   Animations, interactions, and visual design are central to that
    feeling.
-   The homey launcher and Pokémon presence give the app its identity.
-   Real-life activities contributing to Pokémon progression makes
    ordinary activities more motivating.

The reward system already has some motivational value, especially for
easy activities such as: - Completing a five-minute focus session. -
Taking care of Pokémon in CARE.

Rewards are less motivating for more demanding activities at present.
The user still values progression from actively using the other modules.

**Product implication:** Preserve the PMD atmosphere, living-world
presentation, animations, Pokémon interactions, and cross-module
progression. These are core product value, not optional decoration.
Improve the usefulness and usability of modules without stripping away
their game identity.

### General interaction preferences

The user consistently values:

-   Organized information and clear structure.
-   Lists, tags, and precise controls.
-   Flexibility to use the same feature for different purposes.
-   Intuitive defaults and low maintenance overhead.
-   Fast capture for small or time-sensitive actions.
-   Detailed customization when needed, without having to configure
    everything every time.

A recurring pattern across modules is that the user likes powerful
functionality but is discouraged when setup, navigation, or ongoing
maintenance becomes work in itself.

**Guiding principle:** High flexibility and organizational depth, with
low effort to get started and maintain.

------------------------------------------------------------------------

## 2. WORLD

### What the user values and does

WORLD is one of the user's most actively used modules.

The user's preferred expedition loop is:

1.  Collect completed expedition rewards.
2.  Choose new quests.
3.  Send a team suited to those quests.
4.  Return when an expedition is complete or items have been found.

The user likes that expeditions are easy to set up and can run
asynchronously. They generally use **Send My Strongest** rather than
manually selecting a team.

The user described the low-maintenance loop as valuable: dispatch a
team, leave the app, and return when there is something to collect or
act on.

### Team selection friction

Each dungeon has preferred Pokémon types. If the user manually chooses a
team, **type priority matters more than raw strength**.

Current friction described by the user:

-   A previously selected team remains saved even when it is not optimal
    for the next dungeon.
-   The user has to remove the previous team before choosing another.
-   The roster displays 20 Pokémon, requiring a long scroll to find
    alternatives.
-   The user generally uses Send My Strongest, but would like type
    suitability to be prioritized when manually choosing.

### Other WORLD preferences

-   The roster is limited for now; the user sees recruiting and other
    modules as ways the broader system can develop.
-   The user is comfortable with duplicate Pokémon because duplicates
    can be converted into candy and Pokémon can be transferred.
-   The user sees value in expedition speed-up minigames, but considers
    them a lower priority.
-   When asked about the appeal of WORLD, the user's answer emphasized a
    combination of factors, especially rewards and recruitment.

### Design implications

-   Preserve **Send My Strongest** as the quick path.
-   Add a dungeon-aware team recommendation that prioritizes preferred
    types before strength.
-   Make replacing a saved team easy; do not require manually clearing
    the previous selection.
-   Reduce roster scrolling through filters, type grouping, compact
    selection, or a more useful team picker.
-   Keep asynchronous expeditions and reward collection low-maintenance.
-   Treat minigame speed-ups as optional and lower priority than
    team-selection improvements.
-   Preserve duplicate conversion/transfer as a meaningful part of
    roster management.

------------------------------------------------------------------------

## 3. PLAN

### Actual use

The user currently uses: - Tasks. - Custom lists. - Focus sessions. -
Calendar viewing through the PokéLife widget.

The calendar widget is used for viewing; Google Calendar remains the
user's calendar source. The user does not currently report regular use
of PLAN Inbox, Upcoming, recurring tasks, or Goals and Milestones.

### Custom lists

The user's current custom lists are: - **Work:** Tasks related to their
job. - **Resto:** Balai Yllana-related work, such as creating
publications, researching R&D, and website maintenance.

These lists are used to see outstanding work, rather than primarily to
organize today's tasks.

The user prefers to see outstanding items and likes lists, tags, and
precise controls. However, organization should be intuitive and easy to
maintain.

### What the user values in task management

The user previously used TickTick and still values: - Reminders. -
Checklists. - Precise date and time controls. - Flexible recurrence
frequency. - Customizability for different uses.

They used TickTick not only for tasks, but also for grocery lists,
learning, and certification tracking. They no longer use it as much, but
its recurring reminders remain useful for bill payments and household
chores.

### Why PLAN is not used more

The user identified three issues: 1. Quick tasks are forgotten because
they are not captured in the app. 2. Setting up recurring tasks feels
tedious. 3. The UI feels cluttered.

The user uses Focus sessions partly because the reward makes focusing
more motivating.

### Existing tool boundaries

  -----------------------------------------------------------------------
  Tool                                Current role
  ----------------------------------- -----------------------------------
  Google Calendar                     Events, schedules, maps, WFH/RTO
                                      tracking, and tracking whether the
                                      user is staying at the condo or
                                      home

  Samsung Notes                       Quick notes

  TickTick                            Recurring reminders, especially
                                      bill payments and household chores

  PokéLife PLAN                       Tasks, custom Work/Resto lists,
                                      focus sessions, calendar widget
                                      viewing
  -----------------------------------------------------------------------

Google Calendar is more than an event calendar for the user: it is also
a visual tracker of work location and current living location. The user
likes its interface and ability to attach maps and related information.

### Design implications

-   Make quick capture fast enough that small tasks are not forgotten.
-   Retain precise scheduling and recurrence controls; do not remove
    flexibility to make the UI simpler.
-   Use progressive disclosure: basic entry first, detailed controls
    when needed.
-   Improve recurring-task setup with sensible defaults and reusable
    patterns.
-   Keep outstanding tasks easy to review across custom lists.
-   Support varied list purposes, including workstreams, groceries,
    learning, and certification tracking.
-   Do not try to replace Google Calendar wholesale. The existing
    integration and widget have a distinct role.
-   Reduce visual clutter and make the main task surfaces easier to
    scan.

------------------------------------------------------------------------

## 4. CARE

### Actual use

-   The user added routines but does not consistently follow them.
-   Journaling and mood tracking are often forgotten.
-   Breathing exercises are acceptable/useful.
-   Skills are useful when needed.
-   The CARE UI currently feels cluttered.

### Interpretation

The features do not all need to be daily habits: - Routines require
sustainable setup and follow-through. - Journaling and mood tracking
need to be easy to remember and quick to record. - Breathing exercises
are useful as an on-demand activity. - Skills are useful as an on-demand
reference or practice resource.

### Design implications

-   Reduce clutter and make the main purposes of CARE easier to
    distinguish.
-   Make routine creation and ongoing maintenance less burdensome.
-   Improve access to quick journal and mood entries without assuming
    the user wants to journal every day.
-   Keep breathing exercises easy to reach.
-   Make skills discoverable when needed, without forcing them into a
    daily workflow.
-   Avoid treating low daily engagement as proof that an on-demand
    feature is unnecessary.

------------------------------------------------------------------------

## 5. HEAL

### Actual use and value

The user has Huawei Health and considers the HEAL import feature
excellent. They record available health/activity data when it can be
imported.

The user wants HEAL to provide all of the following: - A dashboard of
current health and activity. - Trends over time. - A place to record
health information. - Goals and progress tracking. - Recommendations or
insights based on their data.

Manual recording is less consistent for: - Hydration. - Nutrition. -
Weight.

The user explicitly said these depend on remembering to record them.

### Design implications

-   Preserve and prioritize Huawei Health integration.
-   Make manual logging fast, especially for hydration, nutrition, and
    weight.
-   Support repeat/quick entries and sensible defaults where
    appropriate.
-   Distinguish missing data from a negative result or an actual change.
-   Make dashboard, trends, records, goals, and insights work together.
-   Do not interpret gaps in manual data as lack of interest in those
    features.

------------------------------------------------------------------------

## 6. MINT

### Current state from the user's perspective

The user has not finished setting up MINT. They described setup as
cumbersome and the module as barebones. They have not yet established a
regular workflow or found a finance app that stuck.

Past alternatives: - Money Manager: used in the past, but did not
stick. - Agila: used in the past; the user liked its card-template idea.

The user shared screenshots of MINT's account and activity forms.

### Account setup friction

The account form includes fields such as: - Name. - Account type. -
Currency. - Starting balance. - Bank/provider. - Group. - Last four
digits.

The user said: - Adding accounts feels like a lot. - They dislike manual
setup, though they understand banking apps may not offer an import
workaround comparable to Huawei Health. - They do not understand the
differences between account types. - Card visuals could be improved. -
They do not fully understand how the other MINT features work.

### Activity entry

The activity form offers transaction types such as expense, income,
transfer, refund, fee/interest, and balance adjustment, plus account,
category, date/time, and payee/note controls.

The user has not yet established whether this workflow is convenient in
regular use.

### What the user wants MINT to do

The user wants all of the following: - See money across banks,
e-wallets, and cash in one place. - Know how much is safe to spend. -
Track expenses and understand spending. - Manage bills and payment
schedules. - Track savings and financial goals. - Review net worth and
financial progress. - Receive help with stocks, including what to invest
in and when to sell.

The user recognizes stock selection and sell timing may be too complex.

### Design implications

-   Treat MINT as a comprehensive personal finance hub, not merely an
    expense tracker.
-   Use progressive onboarding: allow the user to start with a small
    number of accounts and add more later.
-   Explain account types in context, with familiar examples.
-   Improve account/card visuals and support clear account identity.
-   Make common transaction entry shorter, while retaining advanced
    transaction types.
-   Explain what budgets, bills, savings goals, net worth, and
    reconciliation do using concrete examples.
-   Do not require the user to understand the whole financial model
    before seeing value.
-   Investment features should be considered a separate, optional future
    scope. Portfolio tracking and research are more tractable starting
    points than personalized buy/sell instructions.

------------------------------------------------------------------------

## 7. HOME

### Intended personal relevance

The user has not used HOME yet. They said it is mainly relevant for
condo management and upkeep, but could also contain irregular household
matters at their other home.

Example given by the user: buying an air conditioner for a room.

### Why HOME has not been used

-   Setup feels tedious.
-   The distinction between chores and maintenance is unclear.
-   Supplies is a mystery: the user does not know what kinds of things
    belong there.
-   Notes feels random and lacks a clear purpose.

### Design implications

Explain the feature boundaries with concrete examples: - **Chores:**
recurring household activities. - **Maintenance:** upkeep, inspection,
servicing, or repair of a home or asset. - **Supplies:** consumable
household items that may need replenishing. - **Notes/references:**
useful household information attached to a room, asset, or relevant
record. - **Household projects/purchases:** irregular plans such as
buying an air conditioner.

Do not require a complete inventory of rooms, assets, and supplies
before HOME becomes useful. Let the user add only what is relevant, when
it becomes relevant.

HOME should support both condo upkeep and occasional tasks for the other
home without making the user maintain two elaborate household databases.

------------------------------------------------------------------------

## 8. LINK

### Actual use

LINK is mostly unused.

### Friction

The user said: - Adding people feels weird. - Information is all over
the place.

### Design implications

-   Make adding a person feel natural and lightweight.
-   Start with a name and allow details to be added progressively.
-   Keep related information together in a coherent person profile.
-   Make the relationship between people, notes, important dates, and
    follow-ups clear.
-   Avoid presenting multiple disconnected data-entry areas without
    explaining how they relate.

The user has not indicated that LINK's underlying purpose is unwanted;
the feedback is primarily about entry and organization.

------------------------------------------------------------------------

## 9. GEAR

### Actual use and value

The user finds the unit converter and currency converter helpful.

They suspect the broader problem is one or more of: - Poor UI. -
Utilities that are too niche. - Features that are too inaccessible.

They suggested that some tools might work better as launcher widgets or
in a separate utility drawer.

### Design implications

-   Make frequently useful utilities accessible without navigating
    deeply into GEAR.
-   Consider launcher shortcuts/widgets for suitable tools such as unit
    and currency converters.
-   Provide a separate, searchable or categorized utility drawer for the
    broader collection.
-   Keep complex tools in dedicated screens.
-   Do not assume every utility needs to be promoted to the launcher;
    prioritize based on usefulness and access frequency.

------------------------------------------------------------------------

## 10. Cross-module product requirements

The interview suggests the following requirements should guide future
work:

### A. Preserve the PMD experience

-   Pokémon and the PMD-inspired world are a major reason the user uses
    PokéLife.
-   The launcher should feel like a living PMD home screen.
-   Animations, interactions, and visual design are core product value.
-   Do not reduce Pokémon to static icons or a thin reward layer.

### B. Make progression reinforce real activity

-   Activities across modules should contribute to Pokémon progression.
-   Rewards can motivate small actions, as demonstrated by the
    five-minute focus session and CARE Pokémon care.
-   The user wants progression to remain meaningful when actively using
    other modules.
-   Avoid making every activity feel like a grind or requiring
    unnecessary engagement.

### C. Reduce setup and maintenance burden

Across PLAN, CARE, MINT, HOME, and LINK, setup friction is a recurring
barrier. - Allow progressive setup. - Use sensible defaults. - Make
optional details optional. - Provide examples and contextual
explanations. - Keep advanced controls available without making them
mandatory.

### D. Keep structure without clutter

The user likes organization and precision, but not interfaces that
expose every option at once. - Clear information hierarchy. - Lists,
tags, filters, and detailed controls where useful. - Progressive
disclosure for advanced options. - Easy scanning of outstanding work and
important information.

### E. Improve accessibility of useful features

-   Quick capture for tasks and health entries.
-   Easy access to frequently used utilities.
-   Discoverable on-demand features.
-   Clear entry points into modules whose purpose is not immediately
    obvious.

### F. Respect existing tools and workflows

PokéLife does not need to replace every existing app: - Google Calendar
remains the preferred event and schedule interface. - Samsung Notes
remains the quick-note tool. - TickTick still provides useful recurring
reminders. - Huawei Health is an important source for HEAL data.

PokéLife should offer a compelling integrated experience and fill gaps,
rather than forcing migration from tools the user already likes.

------------------------------------------------------------------------

## 11. Suggested priority order from interview evidence

This is a UX priority proposal based on reported friction and value, not
a measured engineering-effort estimate.

  -----------------------------------------------------------------------
  Priority                Area                    Reason
  ----------------------- ----------------------- -----------------------
  1                       WORLD team selection    Actively used loop;
                                                  specific, repeated
                                                  friction

  2                       PLAN quick capture and  Desired functionality,
                          recurrence              but setup and clutter
                                                  reduce adoption

  3                       MINT onboarding and     High intended value,
                          explanation             but user cannot
                                                  comfortably get started

  4                       CARE information        Useful features are
                          architecture            forgotten or obscured
                                                  by clutter

  5                       HEAL manual entry       Strong integration
                                                  value; manual data is
                                                  the weak point

  6                       HOME feature clarity    Relevant household use
                          and progressive setup   case, but concepts are
                                                  unclear

  7                       LINK person creation    Unused due to awkward
                          and profile             entry and scattered
                          organization            information

  8                       GEAR access model       Useful converters exist
                                                  but are difficult to
                                                  reach
  -----------------------------------------------------------------------

**Cross-cutting priority:** Preserve and improve the launcher,
animations, interactions, and Pokémon progression throughout the work.
Do not defer the product identity until after the utility features are
complete.

------------------------------------------------------------------------

## 12. What this interview does not establish

This synthesis does not prove: - That an unused feature is
unnecessary. - That a feature is technically broken. - That a reported
UX issue has one specific code-level cause. - That all described
functionality is already implemented. - That a proposed UI change is
validated.

The interview captures the user's reported experience and preferences.
Technical implementation status, data integrity, runtime behavior,
accessibility, and performance require separate inspection and testing.

------------------------------------------------------------------------

## 13. Overall conclusion

The user wants PokéLife to be a substantial personal-life system with a
coherent PMD identity---not a minimal productivity app with Pokémon
decoration.

The strongest opportunity is to preserve its breadth and game atmosphere
while making each module easier to understand, start using, and
maintain. The recurring obstacle is not a lack of interest in
organization or functionality; it is the effort required to configure,
navigate, and consistently use the system.

**Design target:** A rich, flexible, PMD-inspired personal system that
feels alive and rewarding, while remaining intuitive and low-maintenance
in everyday use.
