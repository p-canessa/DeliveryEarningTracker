package com.piero.deliveryearningtracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import com.piero.deliveryearningtracker.utils.incrementOcrScanCount
import androidx.core.graphics.createBitmap

data class OrderData(
    var data: String = "",
    var providerID: Int = 1,
    var tempoImpiegato: Int = 0,
    var numeroOrdini: Int = 0,
    var pagaBase: Double = 0.0,
    var riscossiContanti: Double = 0.0,
    var pagaExtra: Double = 0.0,
    var mancia: Double = 0.0,
    var manciaContanti: Double = 0.0,
    var pagaTotale: Double = 0.0,
    var pagaOraria: Double = 0.0
)

// Data class per il risultato dell'OCR
data class OcrExtractionResult(
    val orderData: OrderData = OrderData(),
    val codice: OcrResultCode
)

// Enum per i codici di risultato
enum class OcrResultCode {
    SUCCESS,                // Estrazione riuscita
    FILE_TOO_LARGE,         // File immagine troppo grande
    IMAGE_LOAD_FAILED,      // Errore nel caricamento dell'immagine
    OCR_PROCESSING_FAILED   // Errore durante l'elaborazione OCR
}

    /**
     * Helper class for performing OCR (Optical Character Recognition) on images.
     *
     * This class provides functionalities to extract text from images, enhance image contrast,
     * parse the extracted text to obtain relevant order data, and handle various date and currency
     * formatting requirements.
     */
    class OCRHelper {

        companion object {

            fun extractDataFromImage(
                context: Context,
                bitmap: Bitmap, // Passiamo il Bitmap invece dell'Uri
                imageUri: Uri, // Uri serve solo per extractDateFromUri
                selectedDate: String,
                useData: Boolean,
                callback: (OcrExtractionResult) -> Unit
            ) {
                try {
                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 75, bitmap.width, bitmap.height - 75)
                    val enhancedBitmap = enhanceImageContrast(croppedBitmap)
                    val image = InputImage.fromBitmap(enhancedBitmap, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text
                            Log.d("OCRHelper", "Testo estratto: $text")
                            incrementOcrScanCount(context)
                            val orderData = parseText(text, imageUri, context, getLanguageCode())
                            if (useData) {
                                orderData.data = selectedDate
                            }
                            callback(OcrExtractionResult(orderData, OcrResultCode.SUCCESS))
                        }
                        .addOnFailureListener { e ->
                            Log.e("OCRHelper", "Errore OCR: ${e.message}")
                            callback(OcrExtractionResult(codice = OcrResultCode.OCR_PROCESSING_FAILED))
                        }
                } catch (e: Exception) {
                    Log.e("OCRHelper", "Errore elaborazione immagine: ${e.message}")
                    callback(OcrExtractionResult(codice = OcrResultCode.OCR_PROCESSING_FAILED))
                }
            }

            private fun enhanceImageContrast(bitmap: Bitmap): Bitmap {
                val contrast = 1.5f
                val brightness = 0f
                val cm = ColorMatrix().apply {
                    set(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, brightness,
                            0f, contrast, 0f, 0f, brightness,
                            0f, 0f, contrast, 0f, brightness,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                }
                val config = bitmap.config ?: Bitmap.Config.ARGB_8888
                val result = createBitmap(bitmap.width, bitmap.height, config)
                val canvas = android.graphics.Canvas(result)
                val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                return result
            }

            private fun parseText(text: String, imageUri: Uri, context: Context, language: String = "it"): OrderData {
                val currencySymbol = CurrencyFormatter.getCurrencySymbol()
                val orderData = OrderData()

                orderData.data = extractDateFromUri(imageUri, context)
                Log.d("OCRHelper", "Data estratta: ${orderData.data}")

                val configs = mapOf(
                    "it" to mapOf(
                        "labels" to listOf("Ordini consegnati", "Pagamento extra", "Mancia"),
                        "riscossiPattern" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2})) ?%s riscossi in contanti", currencySymbol))
                    ),
                    "en" to mapOf(
                        "labels" to listOf("Order fee", "Extra fee", "Tip"),
                        "riscossiPattern" to Pattern.compile(String.format("%s ?(\\d{1,3}(?:[,.]\\d{2})) paid in cash", currencySymbol))
                    ),
                    "xx" to mapOf(
                        "labels" to listOf("Order fee", "Extra fee", "Tip"),
                        "riscossiPattern" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2}) ?%s) paid in cash", currencySymbol))
                    )
                )

                val currencyPatterns = mapOf(
                    "it" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2})) ?%s", currencySymbol)),
                    "en" to Pattern.compile(String.format("%s ?(\\d{1,3}(?:[,.]\\d{2}))", currencySymbol)),
                    "xx" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2}) ?%s)", currencySymbol))
                )

                val currencyPattern = currencyPatterns[language] ?: currencyPatterns["it"]!!
                val config = configs[language] ?: configs["it"]!!
                val labelsList = when (val labels = config["labels"]) {
                    is List<*> -> labels.filterIsInstance<String>()
                    else -> emptyList()
                }
                val riscossiPattern = config["riscossiPattern"] as Pattern

                val timePattern = Pattern.compile("(\\d{2}[:;]\\d{2})[-–](\\d{2}[:;]\\d{2})")

                val detectedLabels = mutableListOf<String>() // Etichette rilevate nel testo
                val values = mutableListOf<Double>() // Valori estratti (escluso il totale)
                val times = mutableListOf<Pair<Int, Int>>()
                var ordersCount = 0
                var parsingValues = false // Flag per passare alla fase dei valori

                val processedLines = text.split("\n").filter { it.isNotBlank() }
                Log.d("OCRHelper", "Lingua: $language")

                for (line in processedLines) {
                    Log.d("OCRHelper", "Analisi riga: $line")

                    val riscossiMatcher = riscossiPattern.matcher(line)
                    if (riscossiMatcher.find()) {
                        val valueStr = riscossiMatcher.group(1)?.replace("[,.]".toRegex(), ".") ?: "0.0"
                        orderData.riscossiContanti = valueStr.toDoubleOrNull() ?: 0.0
                        Log.d("OCRHelper", "Riscossi in contanti: ${orderData.riscossiContanti}")
                        continue
                    }

                    val timeMatcher = timePattern.matcher(line)
                    if (timeMatcher.find()) {
                        val startTime = timeMatcher.group(1) ?: "00:00"
                        val endTime = timeMatcher.group(2) ?: "00:00"
                        val startMinutes = timeToMinutes(startTime)
                        var endMinutes = timeToMinutes(endTime)
                        if (endMinutes < startMinutes) {
                            endMinutes += 1440 // Aggiungi 24 ore (1440 minuti)
                        }
                        times.add(Pair(startMinutes, endMinutes))
                        Log.d("OCRHelper", "Intervallo di tempo: $startTime - $endTime")
                        ordersCount++
                        Log.d("OCRHelper", "Ordine contato: $ordersCount")
                        continue
                    }

                    if (!parsingValues) {
                        val currencyMatcher = currencyPattern.matcher(line)
                        if (currencyMatcher.find()) {
                            parsingValues = true
                            val valueStr = currencyMatcher.group(1)?.replace("[,.]".toRegex(), ".") ?: "0.0"
                            val value = valueStr.toDoubleOrNull() ?: 0.0
                            values.add(value)
                            Log.d("OCRHelper", "Inizio valori, estratto: $value")
                        } else {
                            val matchedLabel = labelsList.find { line.contains(it, ignoreCase = true) }
                            if (matchedLabel != null) {
                                detectedLabels.add(matchedLabel)
                                Log.d("OCRHelper", "Etichetta rilevata: $matchedLabel")
                            }
                        }
                    } else {
                        val currencyMatcher = currencyPattern.matcher(line)
                        if (currencyMatcher.find() && !riscossiPattern.matcher(line).find()) {
                            val valueStr = currencyMatcher.group(1)?.replace("[,.]".toRegex(), ".") ?: "0.0"
                            val value = valueStr.toDoubleOrNull() ?: 0.0
                            values.add(value)
                            Log.d("OCRHelper", "Valore estratto: $value")
                        }
                    }
                }

                // L'ultimo valore è il totale, lo rimuoviamo dai valori da assegnare
                if (values.size > 1) {
                    orderData.pagaTotale = values.last()
                    values.removeAt(values.size - 1)
                    Log.d("OCRHelper", "PagaTotale impostata come ultimo valore: ${orderData.pagaTotale}")
                }

                // Mappa le etichette rilevate ai valori seguendo l'ordine canonico di labelsList
                var valueIndex = 0
                for (label in labelsList) {
                    if (detectedLabels.contains(label) && valueIndex < values.size) {
                        val value = values[valueIndex]
                        when (label) {
                            in listOf("Ordini consegnati", "Order fee") -> orderData.pagaBase = value
                            in listOf("Pagamento extra", "Extra fee") -> orderData.pagaExtra = value
                            in listOf("Mancia", "Tip") -> orderData.mancia = value
                        }
                        Log.d("OCRHelper", "Assegnato $label a $value")
                        valueIndex++
                    }
                }

                // Se pagaTotale non è stato impostato (es. un solo valore), calcolalo
                if (orderData.pagaTotale == 0.0 && values.isNotEmpty()) {
                    orderData.pagaTotale = orderData.pagaBase + orderData.pagaExtra + orderData.mancia
                    Log.d("OCRHelper", "PagaTotale calcolata: ${orderData.pagaTotale}")
                }

                if (times.isNotEmpty()) {
                    val earliestStart = times.minOf { it.first }
                    val latestEnd = times.maxOf { it.second }
                    orderData.tempoImpiegato = kotlin.math.abs(latestEnd - earliestStart)
                    Log.d("OCRHelper", "TempoImpiegato: ${orderData.tempoImpiegato} minuti")
                }

                orderData.numeroOrdini = ordersCount
                Log.d("OCRHelper", "NumeroOrdini: ${orderData.numeroOrdini}")

                if (orderData.riscossiContanti > 0) {
                    orderData.pagaBase -= orderData.riscossiContanti
                    orderData.pagaTotale = orderData.pagaBase + orderData.pagaExtra + orderData.mancia
                    Log.d("OCRHelper", "PagaBase ricalcolata: ${orderData.pagaBase}")
                    Log.d("OCRHelper", "PagaTotale ricalcolata: ${orderData.pagaTotale}")
                }

                if (orderData.tempoImpiegato > 0) {
                    orderData.pagaOraria = (orderData.pagaTotale * 60) / orderData.tempoImpiegato
                    Log.d("OCRHelper", "PagaOraria: ${orderData.pagaOraria}")
                } else {
                    Log.d("OCRHelper", "TempoImpiegato = 0, PagaOraria non calcolata")
                }

                return orderData
            }

            private fun timeToMinutes(time: String): Int {
                val parts = time.split(":",";")
                val hours = parts[0].toInt()
                val minutes = parts[1].toInt()
                return hours * 60 + minutes
            }

            private fun extractDateFromUri(uri: Uri, context: Context): String {
                val datePattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})")
                val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
                var fileName = ""
                try {
                    val cursor = context.contentResolver.query(uri, projection, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            fileName = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                            Log.d("OCRHelper", "fileName: $fileName")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OCRHelper", "Errore durante la query per il nome del file: ${e.message}")
                }

                val matcher = datePattern.matcher(fileName)
                if (matcher.find()) {
                    val year = matcher.group(1)
                    val month = matcher.group(2)
                    val day = matcher.group(3)
                    return "$year-$month-$day"
                } else {
                    Log.d("OCRHelper", "datePattern not found in fileName: $fileName")
                }

                try {
                    val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.Images.ImageColumns.DATE_TAKEN), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val dateTaken = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN))
                            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dateTaken))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OCRHelper", "Errore DATE_TAKEN: ${e.message}")
                }

                return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }

            private fun getLanguageCode(): String {
                val language = Locale.getDefault().language
                return when (language) {
                    "en" -> "en"
                    "it" -> "it"
                    else -> "xx"
                }
            }
        }
    }