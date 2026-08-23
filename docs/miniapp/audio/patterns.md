# Temporal patterns

Patterns are pure, exact and bounded descriptions of note events. They do not play audio and do not depend on tempo. Use `sequence`, `pure`, `stack`, `euclidean`, `choose`, then transform with `fast`, `slow`, `shift`, `repeat`, `every`, or deterministic `degrade`.

```kotlin
private val deterministicMelody = sequence(
    listOf(60, 64, 67, 72).map { AudioNote.Pitched(MidiNote.of(it)) },
).degrade(probability = 0.25f, seed = 42L)
```

The same seed and query arc produce the same events. Choose stable, explicit seeds; do not derive them from wall-clock time. Determinism makes reviews, tests and bug reports reproducible.

Keep patterns bounded. The default query budget allows 4,096 operations and 256 events; the audio compiler additionally rejects declarations that exceed its mobile event/operation budget. Prefer a short pattern plus transforms over materializing a very long note list.

`AudioNote.Rest` creates an explicit rest. `degrade(probability, seed)` removes events with the given probability: `0f` keeps all events and `1f` removes all events.
