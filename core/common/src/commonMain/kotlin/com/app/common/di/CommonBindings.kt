package com.app.common.di

import com.app.common.AppDispatchers
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@ContributesTo(AppScope::class)
@BindingContainer
object CommonBindings {
    @SingleIn(AppScope::class)
    @Provides
    fun provideStoreFactory(): StoreFactory = DefaultStoreFactory()

    @SingleIn(AppScope::class)
    @Provides
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers()

    /**
     * Application-wide [CoroutineScope] backed by a [SupervisorJob] running on
     * [AppDispatchers.default]. Shared by the game engine, repository
     * implementations and anything else that needs a process-lifetime scope.
     */
    @SingleIn(AppScope::class)
    @Provides
    fun provideAppCoroutineScope(dispatchers: AppDispatchers): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}
