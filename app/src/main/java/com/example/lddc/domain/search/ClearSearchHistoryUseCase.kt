package com.example.lddc.domain.search

import com.example.lddc.data.repository.SearchHistoryRepository

class ClearSearchHistoryUseCase(
    private val searchHistoryRepository: SearchHistoryRepository
) {
    suspend operator fun invoke() {
        searchHistoryRepository.clearAllHistory()
    }

    suspend fun deleteItem(id: Long) {
        searchHistoryRepository.deleteSearchHistory(id)
    }

}
