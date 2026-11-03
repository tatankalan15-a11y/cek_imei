package com.cekimei

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Browser mini di dalam aplikasi untuk mencari tipe HP berdasarkan IMEI/TAC. */
class WebActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Cari online"

        val web = WebView(this)
        web.fitsSystemWindows = true
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Toast.makeText(
                        this@WebActivity,
                        "Tidak ada koneksi internet",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        setContentView(web)

        val url = intent.getStringExtra("url") ?: "https://www.google.com"
        if (savedInstanceState == null) web.loadUrl(url) else web.restoreState(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }
}
