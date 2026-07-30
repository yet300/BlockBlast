package ge.yet.blockblast.feature.settings.disableads

import com.app.common.decompose.componentCoroutineScope
import com.arkivanov.decompose.ComponentContext
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultDisableAdsComponent(
    componentContext: ComponentContext,
    private val settings: SettingsRepository,
    private val analytics: AnalyticRepository,
    private val onBackClickedCb: () -> Unit,
    private val coroutineScope: CoroutineScope = componentContext.componentCoroutineScope(),
) : DisableAdsComponent, ComponentContext by componentContext {

    private var disableInProgress = false

    override fun onKeepAdsClicked() {
        analytics.logEvent(
            eventName = "settings_disable_ads_cancelled",
            params = null,
        )
        onBackClickedCb()
    }

    override fun onDisableAdsClicked() {
        if (disableInProgress) return
        disableInProgress = true
        coroutineScope.launch {
            try {
                settings.setAdsEnabled(false)
                analytics.logEvent(
                    eventName = "settings_ads_disabled",
                    params = null,
                )
                onBackClickedCb()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                disableInProgress = false
                analytics.logEvent(
                    eventName = "settings_ads_disable_failed",
                    params = null,
                )
            }
        }
    }

    override fun onBackClicked() = onBackClickedCb()
}
