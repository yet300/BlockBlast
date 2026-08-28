package ge.yet.game.twentyfortyeight

import dev.zacsweers.metro.createGraph
import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppStorage
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import ge.yet.game.miniapp.testkit.RecordingMiniAppSessionHost
import ge.yet.game.miniapp.testkit.TestMiniAppSessionContext
import ge.yet.game.twentyfortyeight.audio.AudioEvent
import ge.yet.game.twentyfortyeight.audio.TwentyFortyEightAudio
import ge.yet.game.twentyfortyeight.di.InspectableTwentyFortyEightAppGraph
import ge.yet.game.twentyfortyeight.di.InspectableTwentyFortyEightSessionGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TwentyFortyEightAudioLifecycleTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `music and sfx suppression stay owned by the host facade without retries`() =
        runTest(dispatcher) {
            val audio = RecordingSessionAudio(musicEnabled = false, sfxEnabled = false)
            val (lifecycle, graph) = sessionGraph(audio)

            advanceUntilIdle()
            assertEquals(1, audio.playMusicAttempts)
            assertEquals(0, audio.acceptedMusicStarts)

            val sfxAttemptsBeforeMove = audio.playSfxAttempts
            graph.audioAdapter.play(AudioEvent.Move)
            assertEquals(sfxAttemptsBeforeMove + 1, audio.playSfxAttempts)
            assertEquals(0, audio.acceptedSfxPlays)
            lifecycle.destroy()
        }

    @Test
    fun `game lifecycle never closes session audio and host closure is terminal`() = runTest(dispatcher) {
        val audio = RecordingSessionAudio()
        val (lifecycle, graph) = sessionGraph(audio)
        advanceUntilIdle()

        lifecycle.destroy()

        assertEquals(0, audio.stopMusicAttempts)
        assertFalse(audio.closed)
        audio.closeByHost()
        val direct = audio.playSfx(TwentyFortyEightAudio.program, TwentyFortyEightAudio.Move)
        assertEquals(
            AudioCommandRejection.SESSION_CLOSED,
            assertIs<AudioCommandResult.Rejected>(direct).reason,
        )
        val attemptsBeforeAdapter = audio.playSfxAttempts
        graph.audioAdapter.play(AudioEvent.Move)
        assertEquals(attemptsBeforeAdapter + 1, audio.playSfxAttempts)
    }

    private fun sessionGraph(
        audio: RecordingSessionAudio,
    ): Pair<MiniAppLifecycleHarness, InspectableTwentyFortyEightSessionGraph> {
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val graph = createGraph<InspectableTwentyFortyEightAppGraph>()
            .factory
            .createInspectableTwentyFortyEightSessionGraph(
                TestMiniAppSessionContext(
                    componentContext = lifecycle.componentContext,
                    visibility = MutableMiniAppVisibilitySource(),
                    host = RecordingMiniAppSessionHost(),
                    storage = MutableMiniAppStorage(),
                    audio = audio,
                ),
            )
        graph.session
        return lifecycle to graph
    }
}

private class RecordingSessionAudio(
    private val musicEnabled: Boolean = true,
    private val sfxEnabled: Boolean = true,
) : MiniAppAudio {
    var closed: Boolean = false
        private set
    var playMusicAttempts: Int = 0
        private set
    var acceptedMusicStarts: Int = 0
        private set
    var stopMusicAttempts: Int = 0
        private set
    var playSfxAttempts: Int = 0
        private set
    var acceptedSfxPlays: Int = 0
        private set

    override fun playMusic(program: AudioProgram): AudioCommandResult {
        playMusicAttempts += 1
        return when {
            closed -> rejected(AudioCommandRejection.SESSION_CLOSED)
            !musicEnabled -> rejected(AudioCommandRejection.PLAYBACK_SUPPRESSED)
            else -> AudioCommandResult.Accepted.also { acceptedMusicStarts += 1 }
        }
    }

    override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult {
        stopMusicAttempts += 1
        return if (closed) rejected(AudioCommandRejection.SESSION_CLOSED) else AudioCommandResult.Accepted
    }

    override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult {
        playSfxAttempts += 1
        return when {
            closed -> rejected(AudioCommandRejection.SESSION_CLOSED)
            !sfxEnabled -> rejected(AudioCommandRejection.PLAYBACK_SUPPRESSED)
            else -> AudioCommandResult.Accepted.also { acceptedSfxPlays += 1 }
        }
    }

    override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
        if (closed) rejected(AudioCommandRejection.SESSION_CLOSED) else AudioCommandResult.Accepted

    fun closeByHost() {
        closed = true
    }

    private fun rejected(reason: AudioCommandRejection): AudioCommandResult =
        AudioCommandResult.Rejected(reason)
}
