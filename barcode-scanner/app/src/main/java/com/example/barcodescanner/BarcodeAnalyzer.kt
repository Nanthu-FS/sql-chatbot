package com.example.barcodescanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Feeds camera frames to ML Kit. Pauses after the first hit so the UI can show it;
 * call [resume] to scan again.
 */
class BarcodeAnalyzer(
    private val onBarcode: (Barcode) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private val paused = AtomicBoolean(false)

    fun resume() = paused.set(false)

    fun close() = scanner.close()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (paused.get() || mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (barcode != null && paused.compareAndSet(false, true)) {
                    onBarcode(barcode)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
