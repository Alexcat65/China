package com.example.chinaprice.ocr

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.chinaprice.model.OcrBlock
import com.example.chinaprice.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class TextAnalyzer(
    private val minIntervalMs: Long = 600,
    private val onResult: (blocks: List<OcrBlock>, width: Int, height: Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private var lastRun = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = imageProxy.image
        if (media == null || now - lastRun < minIntervalMs) {
            imageProxy.close()
            return
        }
        lastRun = now

        val rotation = imageProxy.imageInfo.rotationDegrees
        val w = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
        val h = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
        val input = InputImage.fromMediaImage(media, rotation)

        recognizer.process(input)
            .addOnSuccessListener { text ->
                val blocks = text.textBlocks.map { block ->
                    OcrBlock(block.lines.mapNotNull { line ->
                        line.boundingBox?.let { OcrLine(line.text, it) }
                    })
                }
                onResult(blocks, w, h)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
