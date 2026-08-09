# Shape Catalog Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the Block Blast shape catalog by adding a true `1x1`, removing disconnected diagonals, and making the revive tray exactly `1x1`, horizontal `1x2`, and vertical `1x2`.

**Architecture:** Keep shape definitions and connectivity invariants in `core:domain`. Expose named compact shapes from `ShapeCatalog` so `WeightedShapeGenerator.smallReviveTray()` does not depend on misleading numeric indices. This first stage deliberately leaves adaptive normal-tray generation for a later reviewed task.

**Tech Stack:** Kotlin Multiplatform, `kotlin.test`, Gradle `:core:domain:allTests`

---

### Task 1: Correct compact shapes and revive tray

**Files:**
- Create: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeCatalogTest.kt`
- Modify: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeGeneratorTest.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeCatalog.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeGenerator.kt`

- [x] **Step 1: Add failing catalog invariant tests**

Create `ShapeCatalogTest.kt` with tests that require a true single-cell piece, reject the old diagonal identifiers, and verify orthogonal connectivity for every catalog entry:

```kotlin
package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShapeCatalogTest {

    @Test
    fun catalog_contains_true_single_cell_piece() {
        assertEquals(listOf(Position(0, 0)), ShapeCatalog.SINGLE.cells)
    }

    @Test
    fun catalog_excludes_disconnected_diagonal_shapes() {
        val ids = ShapeCatalog.ALL.mapTo(mutableSetOf()) { it.id }
        assertFalse("diag2_tlbr" in ids)
        assertFalse("diag2_trbl" in ids)
        assertFalse("diag3_tlbr" in ids)
        assertFalse("diag3_trbl" in ids)
    }

    @Test
    fun every_catalog_shape_is_orthogonally_connected() {
        ShapeCatalog.ALL.forEach { shape ->
            val remaining = shape.cells.toMutableSet()
            val reachable = mutableSetOf(remaining.first())
            val frontier = ArrayDeque<Position>().apply { add(remaining.first()) }

            while (frontier.isNotEmpty()) {
                val cell = frontier.removeFirst()
                val neighbors = listOf(
                    Position(cell.x - 1, cell.y),
                    Position(cell.x + 1, cell.y),
                    Position(cell.x, cell.y - 1),
                    Position(cell.x, cell.y + 1),
                )
                neighbors
                    .filter { it in remaining && reachable.add(it) }
                    .forEach(frontier::addLast)
            }

            assertEquals(
                remaining,
                reachable,
                "${shape.id} must be one orthogonally connected polyomino",
            )
            assertEquals(
                shape.cells.size,
                shape.cells.toSet().size,
                "${shape.id} must not contain duplicate cells",
            )
            assertTrue(shape.cells.minOf { it.x } == 0, "${shape.id} must be normalized on x")
            assertTrue(shape.cells.minOf { it.y } == 0, "${shape.id} must be normalized on y")
        }
    }
}
```

Replace `smallReviveTray_is_three_size_two_shapes` in `ShapeGeneratorTest.kt` with:

```kotlin
@Test
fun smallReviveTray_is_single_horizontal_two_and_vertical_two() {
    val tray = gen.smallReviveTray()

    assertEquals(listOf("single", "h2", "v2"), tray.map { it.id })
    assertEquals(listOf(1, 2, 2), tray.map { it.size })
}
```

- [x] **Step 2: Run the domain tests and verify RED**

Run:

```bash
./gradlew :core:domain:allTests
```

Expected: compilation fails because `ShapeCatalog.SINGLE` does not exist, or the new revive assertion fails because the current tray is `h2`, `v2`, `diag2_tlbr`.

- [x] **Step 3: Implement the minimal catalog correction**

In `ShapeCatalog.kt`, define named compact shapes and remove every diagonal entry:

```kotlin
val SINGLE: Polyomino = shape("single", 0 to 0)
val HORIZONTAL_TWO: Polyomino = shape("h2", 0 to 0, 1 to 0)
val VERTICAL_TWO: Polyomino = shape("v2", 0 to 0, 0 to 1)

val SMALL: List<Polyomino> = listOf(
    SINGLE,
    HORIZONTAL_TWO,
    VERTICAL_TWO,
)
```

Delete `diag2_tlbr`, `diag2_trbl`, `diag3_tlbr`, and `diag3_trbl` from the catalog. Leave every connected medium and large shape unchanged.

In `ShapeGenerator.kt`, replace the index-based revive tray with:

```kotlin
override fun smallReviveTray(): List<Polyomino> = listOf(
    ShapeCatalog.SINGLE,
    ShapeCatalog.HORIZONTAL_TWO,
    ShapeCatalog.VERTICAL_TWO,
)
```

- [x] **Step 4: Run focused tests and verify GREEN**

Run:

```bash
./gradlew :core:domain:allTests
```

Expected: all `core:domain` tests pass with zero failures.

- [x] **Step 5: Review the uncommitted diff with the user**

Run:

```bash
git diff --check
git status --short
git diff -- core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeCatalog.kt core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeGenerator.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeCatalogTest.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeGeneratorTest.kt
```

Expected: only the plan plus the four catalog-stage files are uncommitted, with no whitespace errors. Stop for user review.

- [x] **Step 6: Commit only after explicit user approval**

```bash
git add docs/superpowers/plans/2026-08-08-shape-catalog-correction.md core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeCatalog.kt core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeGenerator.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeCatalogTest.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeGeneratorTest.kt
git commit -m "fix(domain): correct compact shape catalog"
```
