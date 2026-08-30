package ge.yet.game.fruitmerge

import ge.yet.game.fruitmerge.engine.ActionResult
import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import ge.yet.game.fruitmerge.engine.FruitMergeRules
import ge.yet.game.fruitmerge.engine.FruitMergeState

internal class TestFruitMergeRules(
    private val delegate: FruitMergeRules = FruitMergeEngine(),
) : FruitMergeRules {
    var stepCalls: Int = 0
        private set
    var paidShakeCalls: Int = 0
        private set

    override fun movePreview(state: FruitMergeState, normalizedX: Float): FruitMergeState =
        delegate.movePreview(state, normalizedX)

    override fun drop(state: FruitMergeState): ActionResult = delegate.drop(state)

    override fun step(state: FruitMergeState, elapsedSeconds: Float): FruitMergeState {
        stepCalls += 1
        return delegate.step(state, elapsedSeconds)
    }

    override fun beginClear(state: FruitMergeState, paid: Boolean): ActionResult =
        delegate.beginClear(state, paid)

    override fun clear(state: FruitMergeState, bodyId: Long, paid: Boolean): ActionResult =
        delegate.clear(state, bodyId, paid)

    override fun cancelClear(state: FruitMergeState): FruitMergeState = delegate.cancelClear(state)

    override fun shake(state: FruitMergeState, paid: Boolean): ActionResult {
        if (paid) paidShakeCalls += 1
        return delegate.shake(state, paid)
    }

    override fun newRun(state: FruitMergeState): FruitMergeState = delegate.newRun(state)
}
