package com.tfassbender.ikbpool.ui

import com.tfassbender.ikbpool.data.model.PoolStatus
import com.tfassbender.ikbpool.domain.Recommendation

data class PoolUiState(
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val status: PoolStatus? = null,
    val recommendation: Recommendation = Recommendation.UNBEKANNT,
    val errorMessage: String? = null,
)
