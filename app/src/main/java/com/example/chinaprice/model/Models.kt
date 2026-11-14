package com.example.chinaprice.model

import android.graphics.Rect

data class OcrLine(val text: String, val box: Rect)

data class OcrBlock(val lines: List<OcrLine>)

data class ParsedItem(val name: String, val box: Rect, val priceCny: Double?)

/** Готовая плашка для отрисовки. box — в координатах кадра анализа (после поворота). */
data class OverlayItem(val box: Rect, val label: String, val sub: String?)

sealed interface ModelState {
    data object Checking : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Error(val message: String) : ModelState
}

data class RateInfo(
    val value: Double,
    val source: String,
    val updatedAt: Long,
    val manual: Boolean
)

data class UiState(
    val model: ModelState = ModelState.Checking,
    val fetchedRate: RateInfo? = null,
    val manualRate: Double? = null,
    val rateLoading: Boolean = false,
    val rateError: String? = null,
    val paused: Boolean = false,
    val torch: Boolean = false,
    val items: List<OverlayItem> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    val rate: RateInfo?
        get() = manualRate?.let { RateInfo(it, "вручную", 0L, true) } ?: fetchedRate
}
