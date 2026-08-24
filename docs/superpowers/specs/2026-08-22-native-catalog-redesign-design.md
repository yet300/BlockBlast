# Native MiniApp Catalog Redesign

## Goal

Replace the editorial featured-card catalog with a direct, native-feeling
MiniApp library inspired by TestFlight, App Store, and Google Play. The screen
uses Material 3 `ListItem` cards, a `CenterAlignedTopAppBar`, adaptive one- and
two-column layouts, a details dialog, and a long-press context menu. The change
must preserve the local immutable registry and the existing Play flow.

## Scope

The first version supports exactly these actions:

- Play a MiniApp.
- Open its details dialog.
- Long-press a card to choose Play or Details.

Share is deliberately excluded. It will be added only with a real MiniApp deep
link routing API. The UI must not show a disabled or placeholder Share action.

The screen has no bottom navigation, tabs, search, sorting, account UI,
download/install progress, ratings, ads, or remote loading/error state.

## Screen Structure

`CatalogContent` renders a transparent Material 3 `Scaffold` over the existing
Root ambient background. Its only app chrome is a `CenterAlignedTopAppBar`
whose title is `Logica`. The bar contains no navigation icon and no actions.
When content scrolls beneath it, the bar may use a restrained Haze backdrop
effect; Haze does not apply to cards or dialogs.

The body is a centered adaptive grid:

- available width below 840 dp: one column;
- available width at least 840 dp: two columns;
- maximum content width: approximately 1200 dp;
- compact outer padding: 16 dp;
- expanded outer padding: 24 dp;
- card spacing: 12–16 dp;
- safe-area ownership belongs to Scaffold/content insets and is applied once.

The empty catalog retains the same app bar and displays a simple centered empty
message with no Retry action.

## MiniApp Card

Each entry is a Material 3 `Card` containing exactly one `ListItem`:

- `leadingContent`: localized MiniApp icon at 64–72 dp;
- `headlineContent`: localized title;
- `supportingContent`: localized description, wrapping up to two or three lines;
- `trailingContent`: compact primary Play button;
- the optional cover is not shown in the list;
- technical category and sort priority are not rendered.

The Play button launches directly. Clicking the remainder of the card opens
details. Long-pressing the card opens an anchored context menu containing only
Play and Details. Selecting either action closes the menu before dispatching
the corresponding component callback. Clicking outside dismisses the menu.

Card semantics must not create nested conflicting click targets: Play remains a
separate named button, while the card surface exposes Details as its primary
click and long-click behavior.

## Details Dialog

Details is a custom Material 3 dialog rather than a new navigation destination.
It contains:

- real cover at the top when `cover != null`;
- otherwise a large real icon on a neutral theme surface;
- icon, localized title, and complete localized description;
- primary Play button;
- explicit Close action.

The dialog does not invent developer name, ratings, reviews, package size,
version, permissions, screenshots, release notes, or category labels because
the manifest does not provide them. The dialog is width-constrained on tablets
instead of expanding into a full screen.

## State Ownership and Restoration

The immutable manifest snapshot remains in `CatalogComponent.Model`. Details
selection is component-owned and exposed as immutable Decompose `Value` state.
The selected `MiniAppId` is represented by a small `@Serializable` saved state
and registered through the component's `StateKeeper`/`saveable` API so an open
dialog survives configuration and process restoration.

The component resolves a restored ID against its manifest snapshot. An unknown
or removed ID normalizes to no selection, so Compose never renders a stale
dialog. Opening, dismissing, and Play dispatch stay on the main thread.

The context-menu anchor and expanded state remain local Compose element state:
they are tied to a transient layout node and must not survive recreation.

## API Shape

`CatalogComponent` gains:

- immutable details selection state;
- `onDetailsRequested(id)`;
- `onDetailsDismissed()`.

The existing `onPlayClicked(id)` remains the only launch callback. No sharing
contract, platform dependency, deep link, or host capability is added.

Plain rendering composables receive state and callbacks rather than a component,
while the screen-level overload subscribes to Decompose values and delegates.
Every layout-emitting composable accepts a `modifier` parameter applied to its
root.

## Interaction and Motion

- Play feedback begins on press, uses a restrained tonal/scale response, and
  commits only on release inside the target.
- Long press may provide platform haptic feedback if an existing portable
  capability is already available; no new vibration dependency is required for
  this task.
- Context menu and dialog transitions use Material defaults and remain
  interruptible.
- Reduced-motion settings remove custom scale/spatial decoration while keeping
  immediate tonal feedback.

## Accessibility and Localization

- Play announces `Play <localized title>`.
- Details and long-click semantics have descriptive accessible labels.
- Focus order follows card order, with the card Details action before its Play
  button where platform semantics permit.
- Touch targets are at least 44 dp; primary actions target 48 dp.
- Long press is never the only route to Details because ordinary click opens it.
- Text wraps under large font scales; cards and dialog content may grow and
  scroll rather than clip.
- Start/end alignment and two-column order mirror correctly in RTL.
- Decorative cover/icon duplication is not announced when adjacent title text
  already provides the same identity.

## Testing

Component tests prove:

- registry order remains unchanged;
- Play forwards the exact ID;
- Details opens for a known ID and dismisses;
- selected details survives StateKeeper restoration;
- an unknown restored ID normalizes to no selection;
- model construction still creates no MiniApp sessions.

Policy/unit tests prove one column below 840 dp and two columns at or above it,
with a centered maximum width.

Compose tests prove:

- `CenterAlignedTopAppBar` displays `Logica` and has no actions;
- cards use the expected title, description, icon, and Play action;
- card click opens details;
- long press exposes only Play and Details;
- dialog renders cover and no-cover variants;
- dialog Play and Close callbacks dispatch exactly once;
- empty state has no Retry;
- compact and expanded layouts render without overlap.

Run common/iOS tests, Android host tests where the module supports them, Android
compilation, iOS simulator compilation, and the affected Root/ComposeApp tests.

## Acceptance Criteria

The redesign is complete when the catalog behaves like a direct native app
library, uses `ListItem` cards in one or two columns, restores its details
selection safely, contains no speculative Share surface, and preserves existing
MiniApp discovery, ordering, and launch behavior.
