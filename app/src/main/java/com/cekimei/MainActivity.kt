package com.cekimei

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private val db = HashMap<String, String>()
    private lateinit var etImei: EditText
    private lateinit var tvResult: TextView
    private lateinit var tvCount: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnOnline: Button
    private lateinit var btnGoogle: Button
    private var pendingTac: String? = null
    private var pendingImei: String? = null

    private val userFile by lazy { File(filesDir, "tac_user.csv") }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importCsv(uri)
    }

    private val scanner by lazy { ImeiScanner() }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            r.data?.getStringExtra("imei")?.let { handleImei(it) }
        }

    private val photoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) scanPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etImei = findViewById(R.id.etImei)
        tvResult = findViewById(R.id.tvResult)
        tvCount = findViewById(R.id.tvCount)
        btnAdd = findViewById(R.id.btnAdd)
        btnOnline = findViewById(R.id.btnOnline)
        btnGoogle = findViewById(R.id.btnGoogle)

        findViewById<Button>(R.id.btnCek).setOnClickListener { runCheck() }
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.btnPhoto).setOnClickListener { photoLauncher.launch("image/*") }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*"))
        }
        findViewById<Button>(R.id.btnSelf).setOnClickListener { showSelfInfo() }
        btnAdd.setOnClickListener { showAddDialog() }
        btnOnline.setOnClickListener {
            val q = pendingImei ?: pendingTac ?: return@setOnClickListener
            openWeb("https://www.imei.info/?imei=$q")
        }
        btnGoogle.setOnClickListener {
            val tac = pendingTac ?: return@setOnClickListener
            openWeb("https://www.google.com/search?q=TAC+$tac+IMEI+model")
        }

        loadDb()
    }

    // ---------- Database ----------

    private fun parseLine(line: String): Pair<String, String>? {
        val parts = line.trim().removePrefix("\uFEFF").split(",", limit = 3)
        if (parts.size < 2) return null
        val tac = parts[0].trim()
        if (!Regex("^\\d{8}$").matches(tac)) return null
        val name = parts.drop(1).joinToString(" ") { it.trim() }.trim()
        if (name.isEmpty()) return null
        return tac to name
    }

    private fun loadDb() {
        db.clear()
        try {
            assets.open("tac.csv").bufferedReader().useLines { seq ->
                seq.forEach { l -> parseLine(l)?.let { db[it.first] = it.second } }
            }
        } catch (_: Exception) {
        }
        if (userFile.exists()) {
            userFile.forEachLine { l -> parseLine(l)?.let { db[it.first] = it.second } }
        }
        tvCount.text = "${db.size} TAC tersimpan di HP ini (offline)"
    }

    private fun importCsv(uri: Uri) {
        var added = 0
        try {
            val sb = StringBuilder()
            contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { seq ->
                seq.forEach { l ->
                    parseLine(l)?.let {
                        sb.append(it.first).append(',').append(it.second).append('\n')
                        added++
                    }
                }
            }
            if (added > 0) userFile.appendText(sb.toString())
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file", Toast.LENGTH_LONG).show()
            return
        }
        loadDb()
        Toast.makeText(this, "$added baris diimpor", Toast.LENGTH_LONG).show()
    }

    // ---------- Cek IMEI ----------

    private fun luhnValid(s: String): Boolean {
        var sum = 0
        for (i in s.indices) {
            var d = s[s.length - 1 - i] - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    private fun runCheck() {
        val n = etImei.text.toString().filter { it.isDigit() }
        setOnlineVisible(false)
        pendingTac = null
        pendingImei = null

        if (n.length < 8) {
            tvResult.text = "Masukkan minimal 8 digit (TAC)."
            return
        }
        val tac = n.substring(0, 8)
        val sb = StringBuilder()
        if (n.length == 15) {
            sb.append("Validasi: ")
                .append(if (luhnValid(n)) "IMEI valid ✓" else "Digit cek salah ✗ (salah ketik?)")
                .append('\n')
            sb.append("Serial: ").append(n.substring(8, 14)).append('\n')
            sb.append("Digit cek: ").append(n[14]).append('\n')
        } else {
            sb.append("Panjang: ${n.length} digit (IMEI normalnya 15)\n")
        }
        sb.append("TAC: ").append(tac).append('\n')
        val hit = db[tac]
        if (hit != null) {
            sb.append("\nTipe HP: ").append(hit)
        } else {
            sb.append("\nTipe HP: belum ada di database.\nCari TAC $tac di internet lalu simpan di sini agar berikutnya langsung ketemu.")
            pendingTac = tac
            pendingImei = if (n.length == 15) n else null
            setOnlineVisible(true)
        }
        tvResult.text = sb.toString()
    }

    private fun setOnlineVisible(show: Boolean) {
        val v = if (show) View.VISIBLE else View.GONE
        btnAdd.visibility = v
        btnOnline.visibility = v
        btnGoogle.visibility = v
    }

    private fun openWeb(url: String) {
        startActivity(Intent(this, WebActivity::class.java).putExtra("url", url))
    }

    private fun showAddDialog() {
        val tac = pendingTac ?: return
        val input = EditText(this).apply { hint = "Contoh: Merek Model" }
        AlertDialog.Builder(this)
            .setTitle("Tipe untuk TAC $tac")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().replace(Regex("[\\r\\n]+"), " ").trim()
                if (name.isNotEmpty()) {
                    userFile.appendText("$tac,$name\n")
                    loadDb()
                    runCheck()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- Info HP sendiri ----------

    private fun showSelfInfo() {
        val msg = "Merek: ${Build.MANUFACTURER}\n" +
            "Model: ${Build.MODEL}\n" +
            "Perangkat: ${Build.DEVICE}\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        AlertDialog.Builder(this)
            .setTitle("Info HP ini")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- Scan kamera / foto ----------

    private fun handleImei(imei: String) {
        etImei.setText(imei)
        runCheck()
    }

    private fun scanPhoto(uri: Uri) {
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka foto", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Membaca foto…", Toast.LENGTH_SHORT).show()
        scanner.scan(image) { imei ->
            if (imei != null) handleImei(imei)
            else Toast.makeText(
                this,
                "IMEI tidak terbaca. Coba foto lebih dekat, terang, dan tidak buram.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.close()
    }
}
