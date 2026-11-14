package com.example.chinaprice.ocr

import android.graphics.Rect
import com.example.chinaprice.model.OcrBlock
import com.example.chinaprice.model.ParsedItem
import kotlin.math.hypot
import kotlin.math.max

object TextParser {

    private val cjk = Regex("[\\u4E00-\\u9FFF]")
    private val symbolBefore = Regex("[¥￥]\\s*(\\d{1,6}(?:[.,]\\d{1,2})?)")
    private val unitAfter =
        Regex("(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(?:元|RMB|rmb|CNY|cny|块钱|块|圆)")
    private val plain = Regex("^\\s*(\\d{1,5}(?:[.,]\\d{1,2})?)\\s*$")
    private val perUnit = Regex("[/／]\\s*[斤个件袋瓶盒克份只包kgKG公]+")

    private class PriceHit(val value: Double, val box: Rect)

    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    private fun clean(text: String): String =
        text.replace(symbolBefore, " ")
            .replace(unitAfter, " ")
            .replace(perUnit, " ")
            .trim()

    fun parse(blocks: List<OcrBlock>, imgW: Int, imgH: Int): List<ParsedItem> {
        val marked = mutableListOf<PriceHit>()
        val plainHits = mutableListOf<PriceHit>()

        for (block in blocks) for (line in block.lines) {
            val m = symbolBefore.find(line.text) ?: unitAfter.find(line.text)
            if (m != null) {
                num(m.groupValues[1])?.let { marked += PriceHit(it, line.box) }
            } else {
                plain.matchEntire(line.text)?.let { pm ->
                    num(pm.groupValues[1])?.let { plainHits += PriceHit(it, line.box) }
                }
            }
        }
        val prices = if (marked.isNotEmpty()) marked else plainHits
        val maxDist = 0.6 * max(imgW, imgH)

        val result = mutableListOf<ParsedItem>()
        val seen = HashSet<String>()

        for (block in blocks) {
            val kept = block.lines.mapNotNull { line ->
                val c = clean(line.text)
                if (cjk.containsMatchIn(c)) c to line.box else null
            }
            if (kept.isEmpty()) continue
            val name = kept.joinToString("") { it.first }
            if (cjk.findAll(name).count() < 2) continue
            if (!seen.add(name)) continue

            val box = Rect(
                kept.minOf { it.second.left },
                kept.minOf { it.second.top },
                kept.maxOf { it.second.right },
                kept.maxOf { it.second.bottom }
            )
            val cx = box.exactCenterX()
            val cy = box.exactCenterY()
            val nearest = prices.minByOrNull {
                hypot((it.box.exactCenterX() - cx).toDouble(), (it.box.exactCenterY() - cy).toDouble())
            }
            val price = nearest?.takeIf {
                hypot((it.box.exactCenterX() - cx).toDouble(), (it.box.exactCenterY() - cy).toDouble()) <= maxDist
            }?.value
            result += ParsedItem(name, box, price)
        }
        return result.sortedBy { it.box.top }.take(8)
    }
}
