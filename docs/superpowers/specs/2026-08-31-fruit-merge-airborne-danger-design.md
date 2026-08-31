# Fruit Merge Airborne Danger Design

## Goal

Prevent a newly dropped fruit from receiving the red danger glow or crying
face while it is still in its initial flight above the danger line. Preserve
warning feedback for fruit that has joined the pile and later approaches or
crosses that line.

## Pile-entry lifecycle

`FruitBody` gains a transient `hasJoinedPile` flag. A body created by a player
drop starts with `false`. The physics step changes it to `true` after the first
contact with the container floor or another fruit. Contact with either side
wall does not change the flag because the fruit can still be in its initial
fall. The flag then remains true for the rest of that body's lifetime,
including subsequent bounces and shake impulses.

A body created by merging two fruits starts with `true`, because the merge is
itself the result of contact inside the pile. Bodies restored from an existing
snapshot also start with `true`; the flag is deliberately not serialized, so
the saved-game schema and compatibility aliases remain unchanged.

## Danger rendering

The pure danger calculation receives the pile-entry state in addition to the
body's top edge and danger-line position. It returns an inactive visual when
`hasJoinedPile` is false. Once the fruit joins the pile, the current warning-band,
glow and crying thresholds remain unchanged.

This explicit lifecycle is preferred over inferring flight from velocity or
position. A new body has zero velocity for its first rendered frame, while
bounces and shake impulses can reverse velocity after the body has already
joined the pile.

## Physics and persistence boundaries

The floor constraint and fruit-pair resolution are the only places that promote
an airborne body into the pile. Wall constraints deliberately preserve the
current value. The flag does not affect mass, restitution,
collision detection, merging, scoring, game-over timing, or drop cooldown.
Persistence continues storing only durable body state; restored bodies are
treated as members of an existing pile.

## Verification

Pure visual-model tests prove that a fruit outside the pile never glows or
cries, even above the danger line, and that the existing pile-member thresholds
still work. Physics tests prove that free fall and side-wall contact keep the
flag false, while floor and fruit contacts make it permanently true. Engine
tests prove that a dropped body starts outside the pile and a merged body starts
inside it. Existing
Fruit Merge tests and Android/iOS compilation remain the regression gate.
