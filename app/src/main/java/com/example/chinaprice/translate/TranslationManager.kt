package com.example.chinaprice.translate

import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class TranslationManager {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
    )
    private val cache = LruCache<String, String>(300)

    /** Китайский -> русский идёт через английский, поэтому проверяем все три модели. */
    suspend fun isDownloaded(): Boolean {
        val manager = RemoteModelManager.getInstance()
        return listOf(TranslateLanguage.CHINESE, TranslateLanguage.RUSSIAN, TranslateLanguage.ENGLISH)
            .all { manager.isModelDownloaded(TranslateRemoteModel.Builder(it).build()).await() }
    }

    suspend fun download() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    suspend fun translate(text: String): String {
        cache.get(text)?.let { return it }
        val result = translator.translate(text).await()
        cache.put(text, result)
        return result
    }

    fun close() = translator.close()
}
