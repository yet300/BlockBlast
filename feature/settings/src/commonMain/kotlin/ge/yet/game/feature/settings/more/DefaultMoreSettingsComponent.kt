package ge.yet.game.feature.settings.more

import com.app.common.decompose.componentCoroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class DefaultMoreSettingsComponent(
    componentContext: ComponentContext,
    private val settings: SettingsRepository,
    private val analytics: AnalyticRepository,
    private val onDisableAdsRequestedCb: () -> Unit,
    private val onLibrariesClickedCb: () -> Unit,
    private val onResetGameDataRequestedCb: () -> Unit,
    private val onBackClickedCb: () -> Unit,
    private val coroutineScope: CoroutineScope = componentContext.componentCoroutineScope(),
) : MoreSettingsComponent, ComponentContext by componentContext {

    private val modelState = MutableValue(
        MoreSettingsComponent.Model(
            adsEnabled = settings.adsEnabled.value,
        ),
    )
    override val model: Value<MoreSettingsComponent.Model> = modelState

    init {
        coroutineScope.launch {
            settings.adsEnabled.collectLatest { enabled ->
                modelState.value = MoreSettingsComponent.Model(adsEnabled = enabled)
            }
        }
    }

    override fun onAdsToggled(enabled: Boolean) {
        if (enabled == modelState.value.adsEnabled) return
        if (!enabled) {
            onDisableAdsRequestedCb()
            return
        }

        coroutineScope.launch {
            try {
                settings.setAdsEnabled(true)
                analytics.logEvent(eventName = "settings_ads_enabled", params = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                analytics.logEvent(eventName = "settings_ads_enable_failed", params = null)
            }
        }
    }

    override fun onSupportClicked() {
        analytics.logEvent(eventName = "settings_support_clicked", params = null)
    }

    override fun onLibrariesClicked() = onLibrariesClickedCb()
    override fun onResetGameDataClicked() = onResetGameDataRequestedCb()
    override fun onBackClicked() = onBackClickedCb()
}
