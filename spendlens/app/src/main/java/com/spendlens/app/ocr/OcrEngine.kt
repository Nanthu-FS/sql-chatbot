package com.spendlens.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.spendlens.app.domain.OcrLine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition (ML Kit, bundled model) — screenshots never leave the phone. */
class OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): List<OcrLine> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val box = line.boundingBox
                        OcrLine(line.text, box?.top ?: 0, box?.bottom ?: 0, box?.left ?: 0, box?.right ?: 0)
                    }
                }
                cont.resume(lines)
            }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
    }
}
