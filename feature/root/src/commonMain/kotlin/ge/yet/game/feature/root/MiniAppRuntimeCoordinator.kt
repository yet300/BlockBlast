package ge.yet.game.feature.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.feature.review.policy.AppReviewPolicy
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.compose.MiniAppPlugin
import ge.yet.game.miniapp.compose.MiniAppRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
internal value class MiniAppSessionKey(val value: Long)

internal class MiniAppRuntimeCoordinator(
    private val registry: MiniAppRegistry,
    private val reviewPolicy: AppReviewPolicy,
    private val analytics: AnalyticRepository,
    private val crashlytics: CrashlyticsRepository,
    initialForeground: Boolean,
    private val closeActiveSession: () -> Unit,
    private val showReview: (MiniAppId, MiniAppReviewOpportunity) -> Boolean,
) {
    private var lastSessionKey = 0L
    private var launchInProgress = false
    private var pendingKey: MiniAppSessionKey? = null
    private var pendingPlugin: MiniAppPlugin? = null
    private var activeKey: MiniAppSessionKey? = null
    private var activeId: MiniAppId? = null
    private var activeVisibilitySource: DefaultMiniAppVisibilitySource? = null
    private var isForeground = initialForeground
    private var isObscured = false

    fun launch(id: MiniAppId, navigate: (MiniAppSessionKey) -> Unit) {
        if (launchInProgress || activeKey != null) return
        launchInProgress = true
        try {
            val plugin = registry[id]
            if (plugin == null) {
                crash { logMessage("miniapp_launch_requested id=${id.value}") }
                publishUnavailableContext(id)
                analytics.logEvent("miniapp_launch_missing", mapOf("miniapp_id" to id.value))
                crash { logMessage("miniapp_launch_missing id=${id.value}") }
                return
            }

            val key = MiniAppSessionKey(++lastSessionKey)
            pendingKey = key
            pendingPlugin = plugin
            crash { logMessage("miniapp_launch_requested id=${id.value} key=${key.value}") }
            navigate(key)
        } finally {
            pendingKey = null
            pendingPlugin = null
            launchInProgress = false
        }
    }

    fun createSession(
        id: MiniAppId,
        key: MiniAppSessionKey,
        componentContext: ComponentContext,
        scope: CoroutineScope,
    ): RootComponent.MiniAppState {
        lastSessionKey = maxOf(lastSessionKey, key.value)
        val visibility = DefaultMiniAppVisibilitySource(currentVisibility())
        val host = BoundMiniAppSessionHost(
            key = key,
            id = id,
            scope = scope,
        )

        activeKey = key
        activeId = id
        activeVisibilitySource = visibility
        publishSessionContext(id, key, visibility.current, state = "creating")
        componentContext.lifecycle.doOnDestroy {
            clearActiveSession(key, visibility)
        }

        val plugin = pendingPlugin.takeIf { pendingKey == key } ?: registry[id]
        if (plugin == null) {
            analytics.logEvent("miniapp_launch_missing", mapOf("miniapp_id" to id.value))
            crash { setCustomValue(MINI_APP_STATE, "unavailable") }
            crash {
                logMessage(
                    "miniapp_launch_missing id=${id.value} key=${key.value} " +
                        "visibility=${visibility.current.name}",
                )
            }
            return RootComponent.MiniAppState.Unavailable(id)
        }

        return try {
            val session = plugin.createSession(componentContext, visibility, host)
            host.arm()
            crash { setCustomValue(MINI_APP_STATE, "active") }
            crash {
                logMessage(
                    "miniapp_session_created id=${id.value} key=${key.value} " +
                        "visibility=${visibility.current.name}",
                )
            }
            RootComponent.MiniAppState.Content(session)
        } catch (error: CancellationException) {
            clearActiveSession(key, visibility)
            scope.cancel()
            throw error
        } catch (error: Throwable) {
            analytics.logEvent(
                "miniapp_launch_failed",
                mapOf(
                    "miniapp_id" to id.value,
                    "error" to (error::class.simpleName ?: "Unknown"),
                ),
            )
            crash { setCustomValue(MINI_APP_STATE, "unavailable") }
            crash { logException(error) }
            RootComponent.MiniAppState.Unavailable(id)
        }
    }

    fun setForeground(value: Boolean) {
        if (isForeground == value) return
        isForeground = value
        updateActiveVisibility()
    }

    fun setObscured(value: Boolean) {
        if (isObscured == value) return
        isObscured = value
        updateActiveVisibility()
    }

    private fun updateActiveVisibility() {
        val key = activeKey ?: return
        val id = activeId ?: return
        val source = activeVisibilitySource ?: return
        val visibility = currentVisibility()
        if (!source.set(visibility)) return
        if (!isActive(key, source)) return

        crash { setCustomValue(MINI_APP_VISIBILITY, visibility.name) }
        if (!isActive(key, source)) return
        crash {
            logMessage(
                "miniapp_visibility_changed id=${id.value} key=${key.value} " +
                    "visibility=${visibility.name}",
            )
        }
    }

    private fun clearActiveSession(
        key: MiniAppSessionKey,
        source: DefaultMiniAppVisibilitySource,
    ) {
        if (!isActive(key, source)) return
        activeKey = null
        activeId = null
        activeVisibilitySource = null
        crash { setCustomValue(MINI_APP_ID, "") }
        if (activeKey != null) return
        crash { setCustomValue(MINI_APP_SESSION_KEY, "") }
        if (activeKey != null) return
        crash { setCustomValue(MINI_APP_VISIBILITY, "") }
        if (activeKey != null) return
        crash { setCustomValue(MINI_APP_STATE, "closed") }
    }

    private fun publishSessionContext(
        id: MiniAppId,
        key: MiniAppSessionKey,
        visibility: MiniAppVisibility,
        state: String,
    ) {
        crash { setCustomValue(MINI_APP_ID, id.value) }
        crash { setCustomValue(MINI_APP_SESSION_KEY, key.value) }
        crash { setCustomValue(MINI_APP_VISIBILITY, visibility.name) }
        crash { setCustomValue(MINI_APP_STATE, state) }
    }

    private fun publishUnavailableContext(id: MiniAppId) {
        crash { setCustomValue(MINI_APP_ID, id.value) }
        crash { setCustomValue(MINI_APP_SESSION_KEY, "") }
        crash { setCustomValue(MINI_APP_VISIBILITY, "") }
        crash { setCustomValue(MINI_APP_STATE, "unavailable") }
    }

    private fun currentVisibility(): MiniAppVisibility = when {
        !isForeground -> MiniAppVisibility.BACKGROUND
        isObscured -> MiniAppVisibility.OBSCURED
        else -> MiniAppVisibility.ACTIVE
    }

    private fun isActive(key: MiniAppSessionKey): Boolean = activeKey == key

    private fun isActive(
        key: MiniAppSessionKey,
        source: DefaultMiniAppVisibilitySource,
    ): Boolean = activeKey == key && activeVisibilitySource === source

    private fun canRequestReview(key: MiniAppSessionKey): Boolean =
        isActive(key) && !isObscured

    private inline fun crash(operation: CrashlyticsRepository.() -> Unit) {
        try {
            crashlytics.operation()
        } catch (_: Exception) {
            // Crash reporting is diagnostic and must not alter runtime behavior.
        }
    }

    private inner class BoundMiniAppSessionHost(
        private val key: MiniAppSessionKey,
        private val id: MiniAppId,
        private val scope: CoroutineScope,
    ) : MiniAppSessionHost {
        private var armed = false
        private var closeDelivered = false

        fun arm() {
            armed = true
        }

        override fun close() {
            if (!armed) return
            scope.launch {
                if (!isActive(key) || closeDelivered) return@launch
                closeDelivered = true
                val visibility = activeVisibilitySource?.current ?: currentVisibility()
                crash {
                    logMessage(
                        "miniapp_session_closed id=${id.value} key=${key.value} " +
                            "visibility=${visibility.name}",
                    )
                }
                if (!isActive(key)) return@launch
                closeActiveSession()
            }
        }

        override fun requestReview(opportunity: MiniAppReviewOpportunity) {
            if (!armed) return
            scope.launch {
                if (!canRequestReview(key)) return@launch
                var acquired = false
                var committed = false
                try {
                    acquired = reviewPolicy.tryAcquirePrompt()
                    if (!acquired || !canRequestReview(key)) return@launch
                    committed = showReview(id, opportunity)
                } finally {
                    if (acquired && !committed) {
                        withContext(NonCancellable) { reviewPolicy.releasePrompt() }
                    }
                }
            }
        }
    }

    private class DefaultMiniAppVisibilitySource(
        initial: MiniAppVisibility,
    ) : MiniAppVisibilitySource {
        private val mutableVisibility = MutableStateFlow(initial)
        override val visibility: StateFlow<MiniAppVisibility> = mutableVisibility.asStateFlow()
        val current: MiniAppVisibility get() = mutableVisibility.value

        fun set(value: MiniAppVisibility): Boolean {
            if (mutableVisibility.value == value) return false
            mutableVisibility.value = value
            return true
        }
    }

    private companion object {
        const val MINI_APP_ID = "mini_app_id"
        const val MINI_APP_SESSION_KEY = "mini_app_session_key"
        const val MINI_APP_VISIBILITY = "mini_app_visibility"
        const val MINI_APP_STATE = "mini_app_state"
    }
}
