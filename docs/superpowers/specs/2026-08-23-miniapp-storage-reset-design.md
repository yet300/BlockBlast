# MiniApp Storage and Game-Data Reset Design

**Status:** Approved design

## Goal

Give every current and future MiniApp a namespaced persistence API without
exposing `multiplatform-settings`, while allowing Settings to delete all
game-owned data safely and report partial failures.

This reset is deliberately narrower than an application reset. It removes
MiniApp saves, scores, achievements, tutorial/onboarding progress and other
game-owned state. It preserves the host theme, audio and motion preferences,
consent, advertising entitlement, review policy and other app settings.

## Ownership and Modules

- `:miniapp:api` owns the Compose-free storage contracts and typed keys.
- A new `:miniapp:storage` module owns the implementation, namespace registry,
  reset coordinator, migration aliases and persistence-library integration.
- `:feature:settings` invokes a host use case; it never discovers plugins or
  accesses raw settings.
- MiniApps consume only their already-namespaced `MiniAppStorage` from the
  session context. They must not import `com.russhwolf.settings.Settings`.

Storage does not belong in `:core:data`: namespace ownership, shipped-plugin
enumeration and the reset lifecycle are MiniApp framework concerns.

## Session Context

Replace the growing positional session factory with one typed context:

```kotlin
interface MiniAppSessionContext {
    val componentContext: ComponentContext
    val visibility: MiniAppVisibilitySource
    val host: MiniAppSessionHost
    val storage: MiniAppStorage
}
```

`MiniAppPlugin.createSession(context)` is intentionally a breaking API change.
The framework currently has one shipped game and one reference plugin, so this
is the cheapest point to remove positional boilerplate. Future capabilities,
including procedural audio, can be added deliberately to the context without
changing every factory signature.

## Storage Contract

Each `MiniAppStorage` instance is permanently bound to one validated
`MiniAppId`. Callers provide only local names; the framework builds physical
keys and prevents access to another MiniApp namespace.

The API supports:

- typed Boolean, Int, Long, Float, Double and String values;
- reactive observation with deterministic initial values;
- removal of one local key and clearing of the current namespace;
- versioned, serializable JSON snapshots with explicit migration functions;
- atomic logical updates and serialized writes on a framework-owned I/O
  dispatcher;
- arbitrary local keys rather than a rigid `save`/`bestScore` schema.

The first implementation may use Multiplatform Settings internally, but that
type is not part of the public contract. A storage backend can therefore change
without editing contributor games.

## Reset Flow

1. The user chooses **Delete all game data** in Settings.
2. Settings shows a destructive confirmation explaining what is and is not
   removed.
3. Root first closes the active MiniApp session and navigates to Catalog.
4. Only after session teardown completes, the reset coordinator enumerates the
   namespaces of all shipped MiniApps.
5. It clears each namespace independently and invokes any registered
   `MiniAppAdditionalDataCleaner` for files or databases outside key/value
   storage.
6. It reports success or a structured partial failure. Failed MiniApp IDs are
   recorded through Crashlytics; the UI does not expose raw exceptions.
7. A retry operates only on namespaces that still contain data or previously
   failed.

Closing the live session before deletion is mandatory. Otherwise an active
store or lifecycle callback can write stale state back immediately after the
reset.

## Legacy Compatibility

Block Blast keeps its existing physical keys until an explicit migration:

- `blockblast.game_save`
- `blockblast.best_score`
- `blockblast.tutorial_seen`

The storage registry declares these as deletion aliases for `game.blockblast`.
New Block Blast values and every new MiniApp use framework namespaces. Merely
introducing this API must not rename existing keys or erase user progress.

## Additional Data Cleaner

`MiniAppAdditionalDataCleaner` is an optional escape hatch for a MiniApp that
owns files, SQLite tables or another approved backend. It is not required for
normal values and snapshots. Cleaners are registered at compile time, receive
no host UI object and must be idempotent.

## Failure and Concurrency Semantics

- Reset is best-effort across namespaces rather than fail-fast.
- Cancellation is rethrown and never reported as a storage failure.
- Individual failures do not stop cleanup of unrelated MiniApps.
- Namespace clearing and active-session creation are serialized by the host so
  a new session cannot start in the middle of a reset.
- Reads after a successful clear immediately observe defaults; caches cannot
  retain deleted state.
- Repeated reset is safe and produces success when nothing remains.

## Verification

Tests must prove namespace isolation, typed round trips, snapshot migrations,
reactive defaults after clear, warm-cache invalidation, idempotence, partial
failure aggregation, cancellation, legacy Block Blast alias deletion and the
Root ordering `close session -> Catalog -> clear`. Integration tests must prove
that both a generated MiniApp and Block Blast can use the same framework API
without raw Settings dependencies.

## Non-Goals

- Full application reset.
- Cloud sync or user accounts.
- Runtime plugin downloads.
- A generic untyped service locator in `MiniAppSessionContext`.
