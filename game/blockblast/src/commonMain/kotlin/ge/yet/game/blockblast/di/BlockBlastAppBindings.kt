package ge.yet.game.blockblast.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import ge.yet.game.blockblast.data.repository.SettingsBackedBestScoreRepository
import ge.yet.game.blockblast.data.repository.SettingsBackedBlockBlastTutorialRepository
import ge.yet.game.blockblast.data.repository.SettingsBackedGameSaveRepository
import ge.yet.game.blockblast.domain.repository.BestScoreRepository
import ge.yet.game.blockblast.domain.repository.BlockBlastTutorialRepository
import ge.yet.game.blockblast.domain.repository.GameSaveRepository
import ge.yet.game.domain.repository.AudioFileProvider

@ContributesTo(AppScope::class)
@BindingContainer
abstract class BlockBlastAppBindings {
    @Binds
    internal abstract val SettingsBackedBestScoreRepository.bindBestScoreRepository:
        BestScoreRepository

    @Binds
    internal abstract val SettingsBackedGameSaveRepository.bindGameSaveRepository: GameSaveRepository

    @Binds
    internal abstract val SettingsBackedBlockBlastTutorialRepository.bindBlockBlastTutorialRepository:
        BlockBlastTutorialRepository

    @Binds
    internal abstract val ComposeAudioFileProvider.bindAudioFileProvider: AudioFileProvider
}
