package com.genshincalc.app

import android.content.Context
import android.net.Uri
import com.genshincalc.core.text.OcrLine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device text recognition of screenshots (ML Kit with the bundled Latin model, so it works offline). */
object ArtifactOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The text lines of an image, with their positions. */
    suspend fun read(context: Context, uri: Uri): List<OcrLine> {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = recognizer.process(image).await()
        return text.textBlocks.flatMap { it.lines }.map { line ->
            val box = line.boundingBox
            OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0)
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
