package com.cekimei

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Layar kamera: arahkan ke IMEI / barcode, otomatis terbaca. */
class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private val scanner by lazy { ImeiScanner() }
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var torchOn = false
    @Volatile private var finished = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else {
                Toast.makeText(this, "Izin kamera diperlukan untuk scan", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        previewView = PreviewView(this)
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val hint = TextView(this).apply {
            text = "Arahkan kamera ke angka IMEI atau barcode di stiker/dus.\nTahan sebentar sampai terbaca."
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 48)
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        val torch = Button(this).apply {
            text = "🔦"
            setOnClickListener {
                val cam = camera ?: return@setOnClickListener
                if (cam.cameraInfo.hasFlashUnit()) {
                    torchOn = !torchOn
                    cam.cameraControl.enableTorch(torchOn)
                }
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        lp.setMargins(0, 48, 24, 0)
        root.addView(torch, lp)

        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @Suppress("UnsafeOptInUsageError")
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || finished) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.scan(image) { imei ->
            proxy.close()
            if (imei != null && !finished) {
                finished = true
                setResult(RESULT_OK, Intent().putExtra("imei", imei))
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        scanner.close()
    }
}
