package com.example.lddc.di

import android.content.Context
import com.example.lddc.data.repository.LocalMusicRepository
import com.example.lddc.data.repository.LocalMusicRepositoryImpl
import com.example.lddc.data.repository.LyricsRepository
import com.example.lddc.data.repository.LyricsRepositoryImpl
import com.example.lddc.domain.usecase.FilterAndSortMusicUseCase
import com.example.lddc.domain.usecase.GetLyricsUseCase
import com.example.lddc.domain.usecase.MatchLocalMusicUseCase
import com.example.lddc.domain.usecase.SearchSongsUseCase
import com.example.lddc.service.LyricsApiService
import com.example.lddc.service.LyricsApiServiceImpl
import com.example.lddc.service.LyricsService

/**
 * 应用依赖注入模块
 *
 * 提供应用级别的依赖注入，替代复杂的DI框架
 * 按照MVVM架构分层管理依赖
 */
object AppModule {

    @Volatile
    private var localMusicRepository: LocalMusicRepository? = null

    @Volatile
    private var lyricsRepository: LyricsRepository? = null

    @Volatile
    private var lyricsApiService: LyricsApiService? = null

    @Volatile
    private var lyricsService: LyricsService? = null

    // ==================== UseCase层 ====================

    @Volatile
    private var searchSongsUseCase: SearchSongsUseCase? = null

    @Volatile
    private var getLyricsUseCase: GetLyricsUseCase? = null

    @Volatile
    private var filterAndSortMusicUseCase: FilterAndSortMusicUseCase? = null

    @Volatile
    private var matchLocalMusicUseCase: MatchLocalMusicUseCase? = null

    // ==================== Repository层 ====================

    /**
     * 提供本地音乐仓库
     */
    fun provideLocalMusicRepository(context: Context): LocalMusicRepository {
        return localMusicRepository ?: synchronized(this) {
            localMusicRepository ?: LocalMusicRepositoryImpl(context.applicationContext).also {
                localMusicRepository = it
            }
        }
    }

    /**
     * 提供歌词API服务
     */
    fun provideLyricsApiService(): LyricsApiService {
        return lyricsApiService ?: synchronized(this) {
            lyricsApiService ?: LyricsApiServiceImpl().also {
                lyricsApiService = it
            }
        }
    }

    /**
     * 提供歌词服务
     */
    fun provideLyricsService(context: Context): LyricsService {
        return lyricsService ?: synchronized(this) {
            lyricsService ?: LyricsService(context.applicationContext).also {
                lyricsService = it
            }
        }
    }

    /**
     * 提供歌词仓库
     */
    fun provideLyricsRepository(context: Context): LyricsRepository {
        return lyricsRepository ?: synchronized(this) {
            lyricsRepository ?: LyricsRepositoryImpl(
                provideLyricsApiService(),
                provideLyricsService(context)
            ).also {
                lyricsRepository = it
            }
        }
    }

    // ==================== UseCase层 ====================

    /**
     * 提供搜索歌曲用例
     */
    fun provideSearchSongsUseCase(context: Context): SearchSongsUseCase {
        return searchSongsUseCase ?: synchronized(this) {
            searchSongsUseCase ?: SearchSongsUseCase(
                provideLyricsRepository(context)
            ).also {
                searchSongsUseCase = it
            }
        }
    }

    /**
     * 提供获取歌词用例
     */
    fun provideGetLyricsUseCase(context: Context): GetLyricsUseCase {
        return getLyricsUseCase ?: synchronized(this) {
            getLyricsUseCase ?: GetLyricsUseCase(
                provideLyricsRepository(context)
            ).also {
                getLyricsUseCase = it
            }
        }
    }

    /**
     * 提供筛选和排序用例
     */
    fun provideFilterAndSortMusicUseCase(): FilterAndSortMusicUseCase {
        return filterAndSortMusicUseCase ?: synchronized(this) {
            filterAndSortMusicUseCase ?: FilterAndSortMusicUseCase().also {
                filterAndSortMusicUseCase = it
            }
        }
    }

    /**
     * 提供本地音乐匹配用例
     */
    fun provideMatchLocalMusicUseCase(context: Context): MatchLocalMusicUseCase {
        return matchLocalMusicUseCase ?: synchronized(this) {
            matchLocalMusicUseCase ?: MatchLocalMusicUseCase(
                provideLocalMusicRepository(context),
                provideSearchSongsUseCase(context),
                provideGetLyricsUseCase(context),
                provideLyricsService(context)
            ).also {
                matchLocalMusicUseCase = it
            }
        }
    }

    /**
     * 重置所有依赖（用于测试）
     */
    fun reset() {
        synchronized(this) {
            localMusicRepository = null
            lyricsRepository = null
            lyricsApiService = null
            lyricsService = null
            searchSongsUseCase = null
            getLyricsUseCase = null
            filterAndSortMusicUseCase = null
            matchLocalMusicUseCase = null
        }
    }
}
