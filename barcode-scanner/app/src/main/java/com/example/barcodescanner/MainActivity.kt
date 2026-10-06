package com.example.barcodescanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var resultCard: View
    private lateinit var formatText: TextView
    private lateinit var valueText: TextView
    private lateinit var openButton: Button
    private lateinit var hintText: View
    private lateinit var permissionLayout: View

    private lateinit var cameraExecutor: ExecutorService
    private var analyzer: BarcodeAnalyzer? = null
    private var lastBarcode: Barcode? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showPermissionUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        resultCard = findViewById(R.id.resultCard)
        formatText = findViewById(R.id.formatText)
        valueText = findViewById(R.id.valueText)
        openButton = findViewById(R.id.openButton)
        hintText = findViewById(R.id.hintText)
        permissionLayout = findViewById(R.id.permissionLayout)

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.scanAgainButton).setOnClickListener { resumeScanning() }
        findViewById<Button>(R.id.copyButton).setOnClickListener { copyResult() }
        openButton.setOnClickListener { openResult() }
        findViewById<Button>(R.id.grantButton).setOnClickListener { onGrantClicked() }

        if (hasCameraPermission()) startCamera() else requestPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        analyzer?.close()
        cameraExecutor.shutdown()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun onGrantClicked() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestPermission.launch(Manifest.permission.CAMERA)
        } else {
            // Permanently denied: send the user to app settings.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionLayout.visibility == View.VISIBLE && hasCameraPermission()) startCamera()
    }

    private fun showPermissionUi() {
        permissionLayout.visibility = View.VISIBLE
        hintText.visibility = View.GONE
    }

    private fun startCamera() {
        permissionLayout.visibility = View.GONE
        hintText.visibility = View.VISIBLE

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            analyzer?.close()
            val barcodeAnalyzer = BarcodeAnalyzer { barcode ->
                runOnUiThread { showResult(barcode) }
            }
            analyzer = barcodeAnalyzer

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, barcodeAnalyzer) }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showResult(barcode: Barcode) {
        lastBarcode = barcode
        previewView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        formatText.text = "${formatName(barcode.format)} · ${typeName(barcode.valueType)}"
        valueText.text = barcode.rawValue ?: barcode.displayValue ?: ""
        openButton.visibility = if (openableUri(barcode) != null) View.VISIBLE else View.GONE

        hintText.visibility = View.GONE
        resultCard.visibility = View.VISIBLE
    }

    private fun resumeScanning() {
        lastBarcode = null
        resultCard.visibility = View.GONE
        hintText.visibility = View.VISIBLE
        analyzer?.resume()
    }

    private fun copyResult() {
        val text = valueText.text ?: return
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("barcode", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun openResult() {
        val uri = lastBarcode?.let { openableUri(it) } ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openableUri(barcode: Barcode): Uri? = when (barcode.valueType) {
        Barcode.TYPE_URL -> barcode.url?.url?.let(Uri::parse)
        Barcode.TYPE_PHONE -> barcode.phone?.number?.let { Uri.parse("tel:$it") }
        Barcode.TYPE_EMAIL -> barcode.email?.address?.let { Uri.parse("mailto:$it") }
        Barcode.TYPE_GEO -> barcode.geoPoint?.let { Uri.parse("geo:${it.lat},${it.lng}") }
        else -> null
    }

    private fun formatName(format: Int) = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        else -> "Unknown"
    }

    private fun typeName(type: Int) = when (type) {
        Barcode.TYPE_URL -> "URL"
        Barcode.TYPE_TEXT -> "Text"
        Barcode.TYPE_PRODUCT -> "Product"
        Barcode.TYPE_ISBN -> "ISBN"
        Barcode.TYPE_PHONE -> "Phone"
        Barcode.TYPE_EMAIL -> "Email"
        Barcode.TYPE_WIFI -> "Wi-Fi"
        Barcode.TYPE_GEO -> "Location"
        Barcode.TYPE_CONTACT_INFO -> "Contact"
        Barcode.TYPE_SMS -> "SMS"
        Barcode.TYPE_CALENDAR_EVENT -> "Event"
        Barcode.TYPE_DRIVER_LICENSE -> "Driver license"
        else -> "Data"
    }
}
