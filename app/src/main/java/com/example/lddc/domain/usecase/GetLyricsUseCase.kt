package com.example.lddc.domain.usecase

import android.util.Log
import com.example.lddc.data.repository.LyricsRepository
import com.example.lddc.model.Lyrics
import com.example.lddc.model.Music

/**
 * 获取歌词用例
 *
 * 封装歌词获取逻辑，统一处理不同来源的歌词
 */
class GetLyricsUseCase(
    private val lyricsRepository: LyricsRepository
) {
    companion object {
        private const val TAG = "GetLyricsUseCase"
    }

    /**
     * 获取歌曲歌词
     *
     * @param music 音乐信息
     * @return 歌词结果
     */
    suspend operator fun invoke(music: Music): Result<Lyrics> {
        return try {
            val result = lyricsRepository.getLyrics(music)
            result.fold(
                onSuccess = { lyrics ->
                    Log.d(TAG, "歌词获取成功: ${music.title}, 长度: ${lyrics.content.length}")
                    Result.success(lyrics)
                },
                onFailure = { error ->
                    Log.e(TAG, "歌词获取失败: ${music.title}, ${error.message}")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取歌词异常", e)
            Result.failure(e)
        }
    }

}
