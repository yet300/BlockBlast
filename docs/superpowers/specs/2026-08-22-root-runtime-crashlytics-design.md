# Root MiniApp Runtime and Crashlytics Design

## Objective

Reduce `DefaultRootComponent` to Decompose navigation and sheet ownership while
making MiniApp session failures diagnosable through the existing
`CrashlyticsRepository`. Preserve all public Root and MiniApp contracts,
serialized navigation state, stale-callback rejection, and session lifecycle
behavior.

## Boundaries

`DefaultRootComponent` continues to own:

- the serialized Catalog/Running child stack;
- the serialized Settings/AppReview slot;
- Back routing and sheet navigation;
- application foreground/background lifecycle forwarding;
- construction of Catalog and sheet children.

An internal `MiniAppRuntimeCoordinator` in `feature:root` owns:

- monotonic session-key allocation and restored-key advancement;
- registry lookup and guarded MiniApp session creation;
- the active visibility source and foreground/obscured state projection;
- session-bound host callbacks and stale-key rejection;
- close and review requests delegated through Root-owned callbacks;
- MiniApp Crashlytics context, breadcrumbs, and non-fatal launch failures.

The coordinator is not a Decompose component and does not create or retain a
coroutine scope. A Running child supplies its lifecycle-bound scope for host
callbacks. Root remains the only owner of Decompose navigation.

## Runtime Flow

1. Catalog asks Root to launch a `MiniAppId`.
2. Root asks the coordinator to reserve a new `SessionKey` and resolve the
   plugin. Missing plugins stay on Catalog and produce analytics plus a
   Crashlytics breadcrumb.
3. Root navigates to serialized `Config.Running(id, key)`.
4. The Running child asks the coordinator to create the visibility source,
   bound host, and plugin session. A restored key advances the coordinator's
   generator before any later launch.
5. Successful creation arms the bound host and publishes the active
   Crashlytics context. A synchronous plugin failure returns
   `MiniAppState.Unavailable`, logs the existing analytics event, and records
   the original exception as non-fatal.
6. Settings/review and app lifecycle changes update the active visibility
   source and its Crashlytics value without recreating the session.
7. Destroying or closing the active Running child clears active runtime state.
   Callbacks carrying an older key are ignored before navigation, review-policy
   access, or Crashlytics mutation.

## Crashlytics Policy

Crashlytics records diagnostic context, not a duplicate analytics stream.

Breadcrumbs:

- `miniapp_launch_requested`;
- `miniapp_launch_missing`;
- `miniapp_session_created`;
- `miniapp_visibility_changed`;
- `miniapp_session_closed`.

Custom values:

- `mini_app_id`;
- `mini_app_session_key`;
- `mini_app_visibility`;
- `mini_app_state`.

`plugin.createSession()` failures are recorded through `logException` with the
context already populated. Normal close, Back, Settings, and review actions are
breadcrumbs or analytics events, never exceptions. Stale callbacks cannot
replace the context of the active session. Crashlytics facade calls are
best-effort and must not break navigation or session creation.

## Coroutine and Lifecycle Rules

- The coordinator stores no `CoroutineScope`.
- Each bound host receives the Running child's `componentContext.coroutineScope()`.
- Host close/review work is cancelled when that Running child is destroyed.
- Navigation remains on Main through Decompose component callbacks/scopes.
- `CancellationException` is never reported as a non-fatal error.
- The app-scoped Crashlytics implementation performs only synchronous facade
  calls and launches no hidden work.

## Testing

TDD adds focused coordinator tests for:

- successful launch context and breadcrumb order;
- missing plugin behavior;
- synchronous session-factory failure and original exception reporting;
- ACTIVE/OBSCURED/BACKGROUND visibility projection;
- active close/review delegation;
- stale close/review rejection without Crashlytics-context mutation;
- restored-key advancement and later session isolation.

The existing Root suite remains the integration contract for serialized
restoration, navigation, sheet behavior, visibility, review reservation, and
session identity. A Root-level regression verifies the extracted coordinator is
wired without changing those behaviors. Android and iOS compilation prove Metro
can provide `CrashlyticsRepository` through the existing telemetry binding.

## Non-goals

- No public MiniApp or Root API changes.
- No new Decompose child/component layer.
- No global exception handler.
- No audio-asset redesign in this change.
- No settings-contract split in this change.
- No changes to BlockBlast Playing/Result navigation.
