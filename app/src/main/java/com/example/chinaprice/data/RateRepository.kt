package com.example.chinaprice.data

import android.content.Context
import com.example.chinaprice.model.RateInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("rates", Context.MODE_PRIVATE)

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 8_000
        }
    }

    fun cached(): RateInfo? {
        val v = prefs.getString("rate", null)?.toDoubleOrNull() ?: return null
        return RateInfo(
            v,
            (prefs.getString("source", "кэш") ?: "кэш") + " (кэш)",
            prefs.getLong("ts", 0L),
            false
        )
    }

    fun manual(): Double? = prefs.getString("manual", null)?.toDoubleOrNull()

    fun setManual(value: Double?) {
        prefs.edit().apply {
            if (value == null) remove("manual") else putString("manual", value.toString())
        }.apply()
    }

    suspend fun fetch(): Result<RateInfo> = withContext(Dispatchers.IO) {
        runCatching { fetchCbr() }
            .recoverCatching { fetchErApi() }
            .onSuccess { info ->
                prefs.edit()
                    .putString("rate", info.value.toString())
                    .putString("source", info.source)
                    .putLong("ts", info.updatedAt)
                    .apply()
            }
    }

    private suspend fun fetchCbr(): RateInfo {
        val body = client.get("https://www.cbr-xml-daily.ru/daily_json.js").bodyAsText()
        val cny = JSONObject(body).getJSONObject("Valute").getJSONObject("CNY")
        val value = cny.getDouble("Value") / cny.getInt("Nominal")
        return RateInfo(value, "ЦБ РФ", System.currentTimeMillis(), false)
    }

    private suspend fun fetchErApi(): RateInfo {
        val body = client.get("https://open.er-api.com/v6/latest/CNY").bodyAsText()
        val value = JSONObject(body).getJSONObject("rates").getDouble("RUB")
        return RateInfo(value, "open.er-api.com", System.currentTimeMillis(), false)
    }
}
