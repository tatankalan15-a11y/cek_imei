package com.cekimei

import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Membaca IMEI (15 digit, lolos Luhn) dari barcode atau teks pada gambar. Berjalan offline. */
class ImeiScanner {

    private val barcode = BarcodeScanning.getClient()
    private val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** [done] dipanggil di main thread dengan IMEI valid, atau null bila tidak ketemu. */
    fun scan(image: InputImage, done: (String?) -> Unit) {
        barcode.process(image).addOnCompleteListener { bt ->
            if (bt.isSuccessful) {
                for (b in bt.result) {
                    val r = b.rawValue?.let { extract(it) }
                    if (r != null) {
                        done(r)
                        return@addOnCompleteListener
                    }
                }
            }
            text.process(image).addOnCompleteListener { tt ->
                done(if (tt.isSuccessful) extract(tt.result.text) else null)
            }
        }
    }

    fun close() {
        barcode.close()
        text.close()
    }

    companion object {
        fun luhnValid(s: String): Boolean {
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

        private val groupRe = Regex("\\d[\\d \\-/]*\\d")

        /** Cari IMEI valid di dalam teks bebas (mis. "IMEI 1: 356938035643809"). */
        fun extract(raw: String): String? {
            for (line in raw.lines()) {
                for (m in groupRe.findAll(line)) {
                    val d = m.value.filter { it.isDigit() }
                    if (d.length < 15) continue
                    if (d.length == 15) {
                        if (luhnValid(d)) return d
                        continue
                    }
                    // 16+ digit: mungkin nomor kecil di depan (mis. "IMEI1 3569...") -> coba tiap jendela 15 digit
                    for (start in (d.length - 15) downTo 0) {
                        val w = d.substring(start, start + 15)
                        if (luhnValid(w)) return w
                    }
                }
            }
            return null
        }
    }
}
