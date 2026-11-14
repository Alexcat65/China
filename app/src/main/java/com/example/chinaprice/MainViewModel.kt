package com.example.chinaprice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chinaprice.data.RateRepository
import com.example.chinaprice.model.ModelState
import com.example.chinaprice.model.OcrBlock
import com.example.chinaprice.model.OverlayItem
import com.example.chinaprice.model.UiState
import com.example.chinaprice.ocr.TextParser
import com.example.chinaprice.translate.TranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val translation = TranslationManager()
    private val rates = RateRepository(app)

    private val _state = MutableStateFlow(
        UiState(fetchedRate = rates.cached(), manualRate = rates.manual())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val busy = AtomicBoolean(false)
    private var emptyFrames = 0

    init {
        prepareModel()
        refreshRate()
    }

    fun prepareModel() {
        viewModelScope.launch {
            _state.update { it.copy(model = ModelState.Checking) }
            try {
                if (!translation.isDownloaded()) {
                    _state.update { it.copy(model = ModelState.Downloading) }
                }
                translation.download()
                _state.update { it.copy(model = ModelState.Ready) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        model = ModelState.Error(
                            e.message ?: "Не удалось скачать языковую модель. Проверьте интернет."
                        )
                    )
                }
            }
        }
    }

    fun refreshRate() {
        viewModelScope.launch {
            _state.update { it.copy(rateLoading = true, rateError = null) }
            rates.fetch()
                .onSuccess { info ->
                    _state.update { it.copy(fetchedRate = info, rateLoading = false) }
                }
                .onFailure {
                    _state.update {
                        it.copy(rateLoading = false, rateError = "Нет сети — используется сохранённый курс")
                    }
                }
        }
    }

    fun setManualRate(text: String) {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return
        if (v <= 0.0) return
        rates.setManual(v)
        _state.update { it.copy(manualRate = v) }
    }

    fun clearManualRate() {
        rates.setManual(null)
        _state.update { it.copy(manualRate = null) }
    }

    fun togglePause() = _state.update { it.copy(paused = !it.paused) }

    fun toggleTorch() = _state.update { it.copy(torch = !it.torch) }

    fun onFrame(blocks: List<OcrBlock>, w: Int, h: Int) {
        val s = _state.value
        if (s.paused || s.model != ModelState.Ready) return
        if (!busy.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val parsed = TextParser.parse(blocks, w, h)
                if (parsed.isEmpty()) {
                    emptyFrames++
                    if (emptyFrames >= 3) {
                        _state.update { it.copy(items = emptyList(), imageWidth = w, imageHeight = h) }
                    }
                } else {
                    emptyFrames = 0
                    val rate = _state.value.rate?.value
                    val items = parsed.map { p ->
                        val tr = runCatching { translation.translate(p.name) }.getOrDefault(p.name)
                            .trim().replaceFirstChar { it.uppercase() }
                        val cny = p.priceCny
                        when {
                            cny == null -> OverlayItem(p.box, tr, null)
                            rate != null -> OverlayItem(p.box, "$tr — ${fmtRub(cny * rate)}", fmtCny(cny))
                            else -> OverlayItem(p.box, "$tr — ${fmtCny(cny)}", "курс недоступен")
                        }
                    }
                    _state.update {
                        if (it.paused) it else it.copy(items = items, imageWidth = w, imageHeight = h)
                    }
                }
            } finally {
                busy.set(false)
            }
        }
    }

    private fun fmtRub(v: Double): String {
        val loc = Locale("ru", "RU")
        val s = if (v >= 100) String.format(loc, "%,.0f", v) else String.format(loc, "%.1f", v)
        return "$s ₽"
    }

    private fun fmtCny(v: Double): String {
        val s = if (v % 1.0 == 0.0) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
        return "$s ¥"
    }

    override fun onCleared() {
        translation.close()
        super.onCleared()
    }
}
