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
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import android.os.Environment
import com.piero.deliveryearningtracker.utils.TimeUtils.timeToMinutes
import java.io.File
import java.io.FileOutputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class OrderData(
    var id: Long = 0L,
    var data: String = "",
    var providerID: Int = 1,
    var tempoImpiegato: Int = 0,
    var numeroOrdini: Int = 0,
    var pagaBase: Double = 0.0,
    var riscossiContanti: Double = 0.0,
    var pagatoContantiRistorante: Double = 0.0,
    var pagaExtra: Double = 0.0,
    var mancia: Double = 0.0,
    var manciaContanti: Double = 0.0,
    var pagaTotale: Double = 0.0,
    var pagaOraria: Double = 0.0,
    var startTime: java.sql.Time = java.sql.Time.valueOf(LocalTime.of(0, 0, 0).format(DateTimeFormatter.ofPattern("HH:mm:ss"))), // Default to 00:00:00
    var endTime: java.sql.Time = java.sql.Time.valueOf(LocalTime.of(0, 0, 0).format(DateTimeFormatter.ofPattern("HH:mm:ss"))),    // Default to 00:00:00
    var orderStrategy: Int = OrderStrategyConstants.NORMAL,
    var batchMasterOrderID: Long? = null,
    var ristorante: String = ""
)

data class OcrExtractionResult(
    val orderData: OrderData = OrderData(),
    val codice: OcrResultCode
)

enum class OcrResultCode {
    SUCCESS,
    FILE_TOO_LARGE,
    IMAGE_LOAD_FAILED,
    OCR_PROCESSING_FAILED
}
// Struttura per gli elementi di testo
data class TextElement(val text: String, val x: Int, val y: Int, val width: Int) {
    val xPlusWidth: Int
        get() = x + width
}

// Configurazioni per Glovo
val glovoLabels = mapOf(
    "it" to mapOf(
        "singleDelivery" to "Consegna singola",
        "acceptanceTime" to "accettazione",
        "completionTime" to "completamento",
        "deliveryFee" to "Costo di consegna",
        "totalEarnings" to "Totale introiti",
        "tip" to "Mancia",
        "promotion" to "promozione inclusa",
        "baseFee" to "Tariffa base",
        "distance" to "Distanza"
    ),
    "en" to mapOf(
        "singleDelivery" to "Single delivery",
        "acceptanceTime" to "Accepted time",
        "completionTime" to "Completed time",
        "deliveryFee" to "Delivery fee",
        "totalEarnings" to "Total income",
        "tip" to "Tip",
        "promotion" to "promo included",
        "baseFee" to "Base fee",
        "distance" to "Distance"
    ),
    "xx" to mapOf(
        "singleDelivery" to "Single delivery",
        "acceptanceTime" to "Acceptance",
        "completionTime" to "Completion",
        "deliveryFee" to "Delivery fee",
        "totalEarnings" to "Total earnings",
        "tip" to "Tip",
        "promotion" to "promotion included",
        "baseFee" to "Base fee",
        "distance" to "Distance"
    )
)

// Configurazioni per Deliveroo
val deliverooLabels = mapOf(
    "it" to mapOf(
        "orderCount" to "Ordini consegnati",
        "startTime" to "Inizio",
        "endTime" to "Fine",
        "totalEarnings" to "Guadagni",
        "tip" to "Mancia",
        "riscossiText" to "riscossi in contanti",
        "extraFee" to "Pagamento extra" // Add this
    ),
    "en" to mapOf(
        "orderCount" to "Orders delivered",
        "startTime" to "Start",
        "endTime" to "End",
        "totalEarnings" to "Earnings",
        "tip" to "Tip",
        "riscossiText" to "collected in cash",
        "extraFee" to "Extra fee" // Add this
    ),
    "xx" to mapOf(
        "orderCount" to "Orders delivered",
        "startTime" to "Start",
        "endTime" to "End",
        "totalEarnings" to "Earnings",
        "tip" to "Tip",
        "riscossiText" to "collected in cash",
        "extraFee" to "Extra fee" // Add this
    )
)

class OCRHelper {

    companion object {

        fun extractDataFromImage(
            context: Context,
            bitmap: Bitmap,
            imageUri: Uri,
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
                        Log.d("OCRHelper", "Testo estratto: ${visionText.text}")
                        incrementOcrScanCount(context)

                        // Determine the provider
                        val language = getLanguageCode()
                        Log.d("OCRHelper", "Lingua rilevata: $language")
                        val provider = detectProvider(visionText.text, language)
                        Log.d("OCRHelper", "Provider rilevato: $provider")

                        val orderData = when (provider) {
                            "Glovo" -> {
                                Log.d("OCRHelper", "Chiamata parseGlovoText")
                                parseGlovoText(visionText, imageUri, context, language) // Passa visionText invece di text
                            }
                            "Deliveroo" -> {
                                Log.d("OCRHelper", "Chiamata parseDeliverooText")
                                parseDeliverooText(visionText, imageUri, context, language) // Passa visionText invece di text
                            }
                            else -> {
                                Log.d("OCRHelper", "Provider sconosciuto, ritorno OrderData vuoto")
                                OrderData()
                            }
                        }

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

        private fun detectProvider(text: String, language: String): String {
            val providerKeywords = mapOf(
                "it" to mapOf(
                    "GlovoHeader" to "Pagamenti",
                    "GlovoFallback" to "Costo di consegna",
                    "DeliverooHeader" to "Ordine",
                    "DeliverooFallback" to "Ordini consegnati"
                ),
                "en" to mapOf(
                    "GlovoHeader" to "Payments",
                    "GlovoFallback" to "Delivery fee",
                    "DeliverooHeader" to "Order",
                    "DeliverooFallback" to "Order fee"
                ),
                "xx" to mapOf(
                    "GlovoHeader" to "Payments",
                    "GlovoFallback" to "Delivery fee",
                    "DeliverooHeader" to "Order",
                    "DeliverooFallback" to "Order fee"
                )
            )

            Log.d("OCRHelper", "Lingua rilevata per detectProvider: $language")
            val keywords = providerKeywords[language] ?: providerKeywords["it"]!!
            Log.d("OCRHelper", "Keywords: $keywords")

            val lines = text.split("\n").filter { it.isNotBlank() }
            val firstThreeLines = lines.take(3).joinToString("\n")
            Log.d("OCRHelper", "Prime tre righe: $firstThreeLines")

            return when {
                firstThreeLines.contains(keywords["GlovoHeader"]!!, ignoreCase = true).also {
                    Log.d("OCRHelper", "Check GlovoHeader (${keywords["GlovoHeader"]}): $it")
                } -> "Glovo"
                firstThreeLines.contains(keywords["DeliverooHeader"]!!, ignoreCase = true).also {
                    Log.d("OCRHelper", "Check DeliverooHeader (${keywords["DeliverooHeader"]}): $it")
                } -> "Deliveroo"
                text.contains(keywords["GlovoFallback"]!!, ignoreCase = true).also {
                    Log.d("OCRHelper", "Check GlovoFallback (${keywords["GlovoFallback"]}): $it")
                } -> "Glovo"
                text.contains(keywords["DeliverooFallback"]!!, ignoreCase = true).also {
                    Log.d("OCRHelper", "Check DeliverooFallback (${keywords["DeliverooFallback"]}): $it")
                } -> "Deliveroo"
                else -> {
                    Log.d("OCRHelper", "Provider non riconosciuto")
                    "Unknown"
                }
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

//        private fun parseDeliverooText(text: String, imageUri: Uri, context: Context, language: String = "it"): OrderData {
//            val currencySymbol = CurrencyFormatter.getCurrencySymbol()
//            val orderData = OrderData()
//
//            orderData.data = extractDateFromUri(imageUri, context)
//            orderData.providerID = 1 // Deliveroo
//            Log.d("OCRHelper", "Data estratta: ${orderData.data}")
//
//            val configs = mapOf(
//                "it" to mapOf(
//                    "labels" to listOf("Ordini consegnati", "Pagamento extra", "Mancia"),
//                    "riscossiPattern" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2})) ?%s riscossi in contanti", currencySymbol))
//                ),
//                "en" to mapOf(
//                    "labels" to listOf("Order fee", "Extra fee", "Tip"),
//                    "riscossiPattern" to Pattern.compile(String.format("%s ?(\\d{1,3}(?:[,.]\\d{2})) paid in cash", currencySymbol))
//                ),
//                "xx" to mapOf(
//                    "labels" to listOf("Order fee", "Extra fee", "Tip"),
//                    "riscossiPattern" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2}) ?%s) paid in cash", currencySymbol))
//                )
//            )
//
//            val currencyPatterns = mapOf(
//                "it" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2})) ?%s", currencySymbol)),
//                "en" to Pattern.compile(String.format("%s ?(\\d{1,3}(?:[,.]\\d{2}))", currencySymbol)),
//                "xx" to Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2}) ?%s)", currencySymbol))
//            )
//
//            val currencyPattern = currencyPatterns[language] ?: currencyPatterns["it"]!!
//            val config = configs[language] ?: configs["it"]!!
//            val labelsList = when (val labels = config["labels"]) {
//                is List<*> -> labels.filterIsInstance<String>()
//                else -> emptyList()
//            }
//            val riscossiPattern = config["riscossiPattern"] as Pattern
//
//            val timePattern = Pattern.compile("(\\d{2}[:;]\\d{2})[-–](\\d{2}[:;]\\d{2})")
//
//            val detectedLabels = mutableListOf<String>()
//            val values = mutableListOf<Double>()
//            val times = mutableListOf<Pair<Int, Int>>()
//            var ordersCount = 0
//            var parsingValues = false
//
//            val processedLines = text.split("\n").filter { it.isNotBlank() }
//            Log.d("OCRHelper", "Lingua: $language")
//
//            for (line in processedLines) {
//                Log.d("OCRHelper", "Analisi riga: $line")
//
//                val riscossiMatcher = riscossiPattern.matcher(line)
//                if (riscossiMatcher.find()) {
//                    val valueStr = riscossiMatcher.group(1)?.replace("[,.]".toRegex(), ".") ?: "0.0"
//                    orderData.riscossiContanti = valueStr.toDoubleOrNull() ?: 0.0
//                    Log.d("OCRHelper", "Riscossi in contanti: ${orderData.riscossiContanti}")
//                    continue
//                }
//
//                val timeMatcher = timePattern.matcher(line)
//                if (timeMatcher.find()) {
//                    val startTime = timeMatcher.group(1) ?: "00:00"
//                    val endTime = timeMatcher.group(2) ?: "00:00"
//                    val startMinutes = timeToMinutes(startTime)
//                    var endMinutes = timeToMinutes(endTime)
//                    if (endMinutes < startMinutes) {
//                        endMinutes += 1440
//                    }
//                    times.add(Pair(startMinutes, endMinutes))
//                    Log.d("OCRHelper", "Intervallo di tempo: $startTime - $endTime")
//                    ordersCount++
//                    Log.d("OCRHelper", "Ordine contato: $ordersCount")
//                    continue
//                }
//
//                val currencyMatcher = currencyPattern.matcher(line)
//                if (currencyMatcher.find()) {
//                    parsingValues = true
//                    val valueStr = currencyMatcher.group(1)?.replace("[,.]".toRegex(), ".") ?: "0.0"
//                    val value = valueStr.toDoubleOrNull() ?: 0.0
//                    values.add(value)
//                    Log.d("OCRHelper", "Valore estratto: $value")
//                    continue
//                }
//
//                if (!parsingValues) {
//                    val matchedLabel = labelsList.find { line.contains(it, ignoreCase = true) }
//                    if (matchedLabel != null) {
//                        detectedLabels.add(matchedLabel)
//                        Log.d("OCRHelper", "Etichetta rilevata: $matchedLabel")
//                    }
//                }
//            }
//
//            val tipValues = mutableListOf<Double>()
//            for (i in detectedLabels.indices) {
//                if (i < values.size) {
//                    val label = detectedLabels[i]
//                    val value = values[i]
//                    when (label) {
//                        in listOf("Ordini consegnati", "Order fee") -> {
//                            orderData.pagaBase = value
//                            Log.d("OCRHelper", "Assegnato $label a $value")
//                        }
//                        in listOf("Pagamento extra", "Extra fee") -> {
//                            orderData.pagaExtra = value
//                            Log.d("OCRHelper", "Assegnato $label a $value")
//                        }
//                        in listOf("Mancia", "Tip") -> {
//                            tipValues.add(value)
//                            Log.d("OCRHelper", "Mancia accumulata: $value")
//                        }
//                    }
//                }
//            }
//
//            orderData.mancia = tipValues.sum()
//            Log.d("OCRHelper", "Totale mance: ${orderData.mancia}")
//
//            if (values.isNotEmpty()) {
//                orderData.pagaTotale = values.last()
//                Log.d("OCRHelper", "PagaTotale impostata come ultimo valore: ${orderData.pagaTotale}")
//            }
//
//            if (orderData.pagaTotale == 0.0) {
//                orderData.pagaTotale = orderData.pagaBase + orderData.pagaExtra + orderData.mancia
//                Log.d("OCRHelper", "PagaTotale calcolata: ${orderData.pagaTotale}")
//            }
//
//            if (times.isNotEmpty()) {
//                val earliestStart = times.minOf { it.first }
//                val latestEnd = times.maxOf { it.second }
//                orderData.tempoImpiegato = kotlin.math.abs(latestEnd - earliestStart)
//                Log.d("OCRHelper", "TempoImpiegato: ${orderData.tempoImpiegato} minuti")
//            }
//
//            orderData.numeroOrdini = ordersCount
//            Log.d("OCRHelper", "NumeroOrdini: ${orderData.numeroOrdini}")
//
//            if (orderData.riscossiContanti > 0) {
//                orderData.pagaBase -= orderData.riscossiContanti
//                orderData.pagaTotale = orderData.pagaBase + orderData.pagaExtra + orderData.mancia
//                Log.d("OCRHelper", "PagaBase ricalcolata: ${orderData.pagaBase}")
//                Log.d("OCRHelper", "PagaTotale ricalcolata: ${orderData.pagaTotale}")
//            }
//
//            if (orderData.tempoImpiegato > 0) {
//                orderData.pagaOraria = (orderData.pagaTotale * 60) / orderData.tempoImpiegato
//                Log.d("OCRHelper", "PagaOraria: ${orderData.pagaOraria}")
//            } else {
//                Log.d("OCRHelper", "TempoImpiegato = 0, PagaOraria non calcolata")
//            }
//
//            return orderData
//        }

        private fun parseDeliverooText(visionText: Text, imageUri: Uri, context: Context, language: String): OrderData {
            val orderData = OrderData()
            orderData.providerID = 1 // Deliveroo

            // Estrai il nome del file e inizializza il log
            val fileName = imageUri.lastPathSegment ?: ""
            LogHelper.initializeLogFile(fileName, context)
            LogHelper.log("OCRHelper", "fileName: $fileName")

            // Estrai la data dal nome del file
            orderData.data = extractDateFromUri(imageUri, context)
            LogHelper.log("OCRHelper", "Data estratta: ${orderData.data}")

            // Seleziona le etichette in base alla lingua
            val labelsForLanguage = deliverooLabels[language] ?: deliverooLabels["xx"] ?: deliverooLabels["en"]!!

            // Variabili per il parsing
            var numeroOrdini = 0
            var pagaBase = 0.0
            var mancia = 0.0
            var pagaExtra = 0.0
            var riscossiContanti = 0.0
            var pagaTotale = 0.0
            val orderNumbers = mutableListOf<String>()
            val timeIntervals = mutableListOf<Pair<Int, Int>>() // Per calcolare il TempoImpiegato
            var startTime: java.sql.Time = minutesToSqlTime(0)
            var endTime: java.sql.Time = minutesToSqlTime(0)

            // Struttura per memorizzare etichette e valori
            //data class TextElement(val text: String, val x: Int, val y: Int, val width: Int)

            val elements = mutableListOf<TextElement>()
            val textBlocks = visionText.textBlocks

            // Estrai tutti gli elementi di testo con coordinate
            for (block in textBlocks) {
                for (line in block.lines) {
                    val boundingBox = line.boundingBox
                    if (boundingBox != null) {
                        val x = boundingBox.left
                        val y = boundingBox.top
                        val width = boundingBox.width()
                        elements.add(TextElement(line.text, x, y, width))
                        LogHelper.log("OCRHelper", "Linea: ${line.text}, x: $x, y: $y, width: $width")
                    }
                }
            }

            // Ordina gli elementi per posizione y (dall'alto verso il basso) e poi per x
            elements.sortWith(compareBy({ it.y }, { it.x }))

            // Calcola una soglia y dinamica basata sulla distanza media tra le righe
            val yDistances = elements.map { it.y }.zipWithNext { a, b -> b - a }.filter { it > 0 }
            val yThreshold = if (yDistances.isNotEmpty()) minOf(yDistances.average().toInt(), 150) else 100
            LogHelper.log("OCRHelper", "Soglia y dinamica: $yThreshold")

            val currencySymbol = CurrencyFormatter.getCurrencySymbol()

            // Estrai il nome del ristorante
            val orderLabel = elements.find { it.text.contains("Ordine", ignoreCase = true) }
            val timeElements = mutableListOf<TextElement>()
            val timePattern = Regex("(\\d{2}[:;]\\d{2})-(\\d{2}[:;]\\d{2})")
            for (element in elements) {
                if (timePattern.containsMatchIn(element.text)) {
                    timeElements.add(element)
                }
            }
            val earliestTime = timeElements.minByOrNull { it.y }

            if (orderLabel != null && earliestTime != null) {
                /*val candidateRestaurant = elements.filter { it.y > orderLabel.y && it.y < earliestTime.y }
                    .filter { it.text.matches(Regex("[a-zA-Z]+[a-zA-Z0-9]*")) && !it.text.all { it.isDigit() } }
                    .minByOrNull { it.y - orderLabel.y }*/
                val potentialCandidates = elements.filter { it.y > orderLabel.y && it.y < earliestTime.y }
                val filteredCandidates = potentialCandidates.filter { it.text.trim().matches(Regex("[\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u017F][\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u017F0-9\\p{Punct}\\s]*")) && !it.text.trim().all { it.isDigit() } }
                val candidateRestaurant = filteredCandidates.minByOrNull { it.y - orderLabel.y }
                LogHelper.log("OCRHelper", "Candidati potenziali: ${potentialCandidates.map { it.text }}")
                LogHelper.log("OCRHelper", "Candidati filtrati: ${filteredCandidates.map { it.text }}")
                LogHelper.log("OCRHelper", "Candidato selezionato: ${candidateRestaurant?.text ?: "null"}")
                if (candidateRestaurant != null) {
                    orderData.ristorante = candidateRestaurant.text.trim()
                    LogHelper.log("OCRHelper", "Ristorante rilevato (Deliveroo): ${orderData.ristorante}")
                } else {
                    LogHelper.log("OCRHelper", "Nessun candidato ristorante valido trovato")
                }
            } else {
                LogHelper.log("OCRHelper", "Ordine o tempo non rilevati per estrarre il ristorante")
            }

            // Regex per identificare importi, tempi e numeri ordini
            val currencyPattern = getCurrencyPatterns(currencySymbol)[language] ?: getCurrencyPatterns(currencySymbol)["xx"]!!
            val riscossiPattern = Regex(
                ".*?(\\d+[,.]\\d{2}) ?$currencySymbol\\s*(?:${labelsForLanguage["riscossiText"]})",
                RegexOption.IGNORE_CASE
            )
            val areaCodePattern = Regex("(?:O\\s*)?([A-Z0-9]{4,6})(?:\\s*(\\d{1,4}))?")
            val orderNumberOnlyPattern = Regex("^\\d{1,4}$")

            // Lista di parole comuni nei nomi dei ristoranti da escludere
            val restaurantKeywords = setOf(
                "Burger", "Sushi", "Sushiko", "Happy", "Days", "Creperia", "Yogurteria", "Delizie", "Crêpes", "Mancia", "Ordine", "Ordini", "Pagamento"
            )

            // Liste per etichette, valori e frammenti di numeri ordini
            val labels = mutableListOf<TextElement>()
            val values = mutableListOf<TextElement>()
            val orderNumberFragments = mutableListOf<Pair<String, TextElement>>()

            // Primo passaggio: raccogli orari, etichette, valori e frammenti
            for (element in elements) {
                LogHelper.log("OCRHelper", "Analisi elemento: ${element.text}, x: ${element.x}, y: ${element.y}")

                // Ignora elementi di rumore
                if (element.text in listOf("<", "||", "O")) {
                    LogHelper.log("OCRHelper", "Elemento ignorato (rumore): ${element.text}")
                    continue
                }

                // Raccogli orari
                val timeMatch = timePattern.find(element.text)
                if (timeMatch != null) {
                    val startTimeStr = timeMatch.groupValues[1]
                    val endTimeStr = timeMatch.groupValues[2]
                    LogHelper.log("OCRHelper", "Intervallo di tempo: $startTimeStr - $endTimeStr")

                    val startMinutes = timeToMinutes(startTimeStr)
                    var endMinutes = timeToMinutes(endTimeStr)
                    if (endMinutes < startMinutes) {
                        endMinutes += 24 * 60
                    }

                    timeIntervals.add(Pair(startMinutes, endMinutes))
                    numeroOrdini++
                    timeElements.add(element)
                    LogHelper.log("OCRHelper", "Ordine contato: $numeroOrdini")
                }

                // Raccogli frammenti di numeri ordini
                val areaMatch = areaCodePattern.find(element.text)
                if (areaMatch != null && !element.text.contains(labelsForLanguage["tip"]!!, ignoreCase = true)) {
                    val areaCode = areaMatch.groupValues[1]
                    val number = areaMatch.groupValues[2].takeIf { it.isNotEmpty() }
                    if (restaurantKeywords.none { areaCode.contains(it, ignoreCase = true) }) {
                        val orderNumber = if (number != null) "$areaCode $number" else areaCode
                        orderNumberFragments.add(orderNumber to element)
                        LogHelper.log("OCRHelper", "Frammento ordine trovato: $orderNumber")
                    }
                } else if (orderNumberOnlyPattern.matches(element.text) &&
                    !currencyPattern.containsMatchIn(element.text) &&
                    !element.text.contains(labelsForLanguage["tip"]!!, ignoreCase = true)) {
                    orderNumberFragments.add(element.text to element)
                    LogHelper.log("OCRHelper", "Numero ordine standalone trovato: ${element.text}")
                }

                // Raccogli etichette e valori
                if (element.text.contains(labelsForLanguage["orderCount"]!!, ignoreCase = true) ||
                    element.text.contains(labelsForLanguage["extraFee"]!!, ignoreCase = true) ||
                    element.text.contains(labelsForLanguage["tip"]!!, ignoreCase = true) ||
                    riscossiPattern.containsMatchIn(element.text)) {
                    labels.add(element)
                    LogHelper.log("OCRHelper", "Etichetta trovata: ${element.text}, x: ${element.x}, y: ${element.y}")
                } else if (currencyPattern.containsMatchIn(element.text)) {
                    val currencyMatch = currencyPattern.find(element.text)
                    if (currencyMatch != null) {
                        var cleanedValue = currencyMatch.value.replace(Regex("""\s+"""), "")
                        LogHelper.log("OCRHelper", "Valore trovato: $cleanedValue, x: ${element.x}, y: ${element.y}, xPlusWidth: ${element.xPlusWidth}")
                        values.add(element.copy(text = cleanedValue))
                    } else {
                        LogHelper.log("OCRHelper", "Valore non riconosciuto per ${element.text}, pattern: $currencyPattern")
                    }
                }
            }

            // Calcola il TempoImpiegato
            var tempoTotaleMinuti = 0
            if (timeIntervals.isNotEmpty()) {
                val earliestStart = timeIntervals.minOf { it.first }
                val latestEnd = timeIntervals.maxOf { it.second }
                tempoTotaleMinuti = latestEnd - earliestStart
                startTime = minutesToSqlTime(earliestStart)
                endTime = minutesToSqlTime(latestEnd)
                LogHelper.log("OCRHelper", "TempoImpiegato calcolato: $tempoTotaleMinuti minuti")
            }

            // Secondo passaggio: associa frammenti agli orari
            for (timeElement in timeElements) {
                val nearbyFragments = orderNumberFragments
                    .filter { abs(it.second.y - timeElement.y) < yThreshold }
                    .sortedBy { it.second.x }

                val completeFragment = nearbyFragments.find { it.first.matches(Regex("[A-Z0-9]{4,6}\\s+\\d{1,4}")) }
                var orderNumber = ""

                if (completeFragment != null) {
                    orderNumber = completeFragment.first
                    LogHelper.log("OCRHelper", "Frammento completo trovato: $orderNumber")
                } else {
                    val areaFragments = nearbyFragments.filter { it.first.matches(Regex("[A-Z0-9]{4,6}")) }
                    val numberFragments = nearbyFragments.filter { it.first.matches(Regex("\\d{1,4}")) }

                    if (areaFragments.isNotEmpty()) {
                        orderNumber = areaFragments.first().first
                        val closestNumber = numberFragments.minByOrNull {
                            abs(it.second.y - areaFragments.first().second.y) + abs(it.second.x - areaFragments.first().second.x)
                        }
                        if (closestNumber != null && abs(closestNumber.second.y - areaFragments.first().second.y) < yThreshold / 2) {
                            orderNumber = "${areaFragments.first().first} ${closestNumber.first}"
                        }
                    } else if (numberFragments.isNotEmpty()) {
                        orderNumber = numberFragments.first().first
                    }
                }

                if (orderNumber.isNotEmpty() && !orderNumbers.contains(orderNumber)) {
                    orderNumbers.add(orderNumber)
                    LogHelper.log("OCRHelper", "Numero ordine associato: $orderNumber")
                    orderNumberFragments.removeAll { it.first == orderNumber || it.first in orderNumber.split(" ") }
                }
            }

            // Associa etichette e valori
            val financialValues = values.sortedBy { it.y }.toMutableList()
            val totalValue = financialValues.maxByOrNull { it.y } // Identifica il totale (valore con y più alta)
            if (totalValue != null) {
                val currencyMatch = currencyPattern.find(totalValue.text)
                pagaTotale = currencyMatch?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                LogHelper.log("OCRHelper", "Paga totale assegnata: $pagaTotale")
                financialValues.remove(totalValue) // Rimuovi il totale dalla lista
            }

            for (label in labels) {
                var value: Double? = null

                if (riscossiPattern.containsMatchIn(label.text)) {
                    val match = riscossiPattern.find(label.text)
                    if (match != null) {
                        val valueText = match.groupValues[1]
                        value = parseCurrency(valueText, currencySymbol)
                        riscossiContanti = value
                        LogHelper.log("OCRHelper", "Riscossi in contanti assegnati: $riscossiContanti")
                    }
                } else {
                    // Trova il valore con la distanza verticale minima dall'etichetta
                    val closestValue = financialValues.minByOrNull { v ->
                        abs(v.y - label.y)
                    }
                    if (closestValue != null && abs(closestValue.y - label.y) < yThreshold) {
                        val currencyMatch = currencyPattern.find(closestValue.text)
                        value = currencyMatch?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                        LogHelper.log("OCRHelper", "Valore associato a ${label.text}: $value (y: ${closestValue.y}, etichetta y: ${label.y})")
                        financialValues.remove(closestValue)
                    } else {
                        LogHelper.log("OCRHelper", "Nessun valore trovato per ${label.text} entro la soglia y: $yThreshold")
                    }
                }

                if (value != null) {
                    when {
                        label.text.contains(labelsForLanguage["orderCount"] ?: "Ordini consegnati", ignoreCase = true) -> {
                            pagaBase = value
                            LogHelper.log("OCRHelper", "Paga base assegnata: $pagaBase")
                        }
                        label.text.contains(labelsForLanguage["extraFee"] ?: "Pagamento extra", ignoreCase = true) -> {
                            pagaExtra = value
                            LogHelper.log("OCRHelper", "Pagamento extra assegnato: $pagaExtra")
                        }
                        label.text.contains(labelsForLanguage["tip"] ?: "Mancia", ignoreCase = true) -> {
                            mancia += value
                            LogHelper.log("OCRHelper", "Mancia aggiunta: $value, Totale mance: $mancia")
                        }
                    }
                }
            }

            // Verifica la coerenza con il totale
            val calculatedTotal = pagaBase + pagaExtra + mancia
            if (abs(calculatedTotal - pagaTotale) > 0.1) {
                LogHelper.log("OCRHelper", "Attenzione: il totale calcolato ($calculatedTotal) non corrisponde al totale nello screenshot ($pagaTotale)")
            }

            // Sottrai riscossi in contanti
            if (riscossiContanti > 0) {
                pagaBase -= riscossiContanti
                pagaTotale = pagaBase + pagaExtra + mancia
                LogHelper.log("OCRHelper", "Paga base ricalcolata dopo riscossi: $pagaBase")
                LogHelper.log("OCRHelper", "Paga totale ricalcolata dopo riscossi: $pagaTotale")
            }

            // Impedisci valori negativi
            pagaBase = maxOf(0.0, pagaBase)
            pagaTotale = maxOf(0.0, pagaTotale)

            // Calcola paga oraria
            val pagaOraria = if (tempoTotaleMinuti > 0) (pagaTotale * 60) / tempoTotaleMinuti else 0.0

            // Imposta valori in OrderData
            orderData.numeroOrdini = numeroOrdini
            orderData.tempoImpiegato = tempoTotaleMinuti
            orderData.pagaBase = pagaBase
            orderData.pagaExtra = pagaExtra
            orderData.mancia = mancia
            orderData.riscossiContanti = riscossiContanti
            orderData.pagaTotale = pagaTotale
            orderData.pagaOraria = pagaOraria
            orderData.startTime = startTime
            orderData.endTime = endTime

            LogHelper.log("OCRHelper", "Paga base finale: $pagaBase")
            LogHelper.log("OCRHelper", "Paga extra finale: $pagaExtra")
            LogHelper.log("OCRHelper", "Mancia finale: $mancia")
            LogHelper.log("OCRHelper", "Riscossi in contanti finale: $riscossiContanti")
            LogHelper.log("OCRHelper", "Paga totale finale: $pagaTotale")
            LogHelper.log("OCRHelper", "TempoImpiegato: $tempoTotaleMinuti minuti")
            LogHelper.log("OCRHelper", "NumeroOrdini: $numeroOrdini")
            LogHelper.log("OCRHelper", "PagaOraria: $pagaOraria")
            LogHelper.log("OCRHelper", "Numeri ordini: ${orderNumbers.joinToString(",")}")

            LogHelper.closeLogFile()
            return orderData
        }
        private fun getCurrencyPatterns(currencySymbol: String): Map<String, Regex> {
            val escapedSymbol = Regex.escape(currencySymbol) // Escape special characters like $
            return mapOf(
                "it" to Regex("""(?:\p{So}\s*)?(\d{1,3}(?:[,.]\d{2}))\s*$escapedSymbol(?=\s|$|\D)"""),
                "en" to Regex("""(?:\p{So}\s*)?$escapedSymbol\s*(\d{1,3}(?:[,.]\d{2}))(?=\s|$|\D)"""),
                "xx" to Regex("""(?:\p{So}\s*)?(?:$escapedSymbol\s*(\d{1,3}(?:[,.]\d{2}))|(\d{1,3}(?:,\d{2}))\s*$escapedSymbol)(?=\s|$|\D)""")
            )
        }

        private fun parseCurrency(value: String, currencySymbol: String): Double {
            val cleaned = value.replace(Regex("""[$currencySymbol\s]"""), "") // Remove symbol and spaces
            return when {
                cleaned.contains(",") -> cleaned.replace(",", ".").toDoubleOrNull() ?: 0.0
                cleaned.contains(".") -> cleaned.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }

//        private fun parseGlovoText(visionText: Text, imageUri: Uri, context: Context, language: String = "it"): OrderData {
//            val currencySymbol = CurrencyFormatter.getCurrencySymbol()
//            val orderData = OrderData()
//            val text = visionText.text
//
//            orderData.data = extractDateFromUri(imageUri, context)
//            orderData.providerID = 2 // Glovo
//            Log.d("OCRHelper", "Data estratta: ${orderData.data}")
//
//            // Define language-specific labels for Glovo
//            val glovoLabels = mapOf(
//                "it" to mapOf(
//                    "singleDelivery" to "Consegna singola",
//                    "acceptanceTime" to "Ora di accettazione",
//                    "completionTime" to "Ora di completamento",
//                    "deliveryFee" to "Costo di consegna",
//                    "totalEarnings" to "Totale introiti",
//                    "tip" to "Mancia",
//                    "promotion" to "promozione inclusa"
//                ),
//                "en" to mapOf(
//                    "singleDelivery" to "Single delivery",
//                    "acceptanceTime" to "Acceptance time",
//                    "completionTime" to "Completion time",
//                    "deliveryFee" to "Delivery fee",
//                    "totalEarnings" to "Total earnings",
//                    "tip" to "Tip",
//                    "promotion" to "promotion included"
//                ),
//                "xx" to mapOf(
//                    "singleDelivery" to "Single delivery",
//                    "acceptanceTime" to "Acceptance time",
//                    "completionTime" to "Completion time",
//                    "deliveryFee" to "Delivery fee",
//                    "totalEarnings" to "Total earnings",
//                    "tip" to "Tip",
//                    "promotion" to "promotion included"
//                )
//            )
//
//            val labels = glovoLabels[language] ?: glovoLabels["it"]!!
//
//            val currencyPattern = Pattern.compile(String.format("(\\d{1,3}(?:[,.]\\d{2})) ?%s", currencySymbol))
//            val timePattern = Pattern.compile("(\\d{2}:\\d{2})")
//
//            val processedLines = text.split("\n").filter { it.isNotBlank() }
//            Log.d("OCRHelper", "Lingua: $language")
//
//            // Lists to store labels and values
//            val labelList = mutableListOf<Pair<String, Int>>() // (label, line index)
//            val currencyList = mutableListOf<Pair<Double, Int>>() // (currency value, line index)
//            val timeList = mutableListOf<Pair<String, Int>>() // (time value, line index)
//
//            // Struttura per memorizzare etichette e valori
//            data class TextElement(val text: String, val x: Int, val y: Int, val width: Int)
//            val elements = mutableListOf<TextElement>()
//            val textBlocks = visionText.textBlocks
//            // Estrai tutti gli elementi di testo con coordinate
//            for (block in textBlocks) {
//                for (line in block.lines) {
//                    val boundingBox = line.boundingBox
//                    if (boundingBox != null) {
//                        val x = boundingBox.left
//                        val y = boundingBox.top
//                        val width = boundingBox.width()
//                        elements.add(TextElement(line.text, x, y, width))
//                        Log.d("OCRHelper", "Linea: ${line.text}, x: $x, y: $y, width: $width")
//                    }
//                }
//            }
//            // Variables to track state
//            var ordersCount = 0
//
//            // First pass: Collect labels, currency values, and times
//            for (i in processedLines.indices) {
//                val line = processedLines[i]
//                Log.d("OCRHelper", "Analisi riga: $line")
//
//                // Detect "Consegna singola"
//                if (line.contains(labels["singleDelivery"]!!, ignoreCase = true)) {
//                    ordersCount = 1
//                    labelList.add(Pair("singleDelivery", i))
//                    Log.d("OCRHelper", "Ordine contato: $ordersCount")
//                    continue
//                }
//
//                // Handle "Ora di" for acceptance and completion times
//                if (line.contains("Ora di", ignoreCase = true)) {
//                    // Look ahead for "accettazione" or "completamento" in the next few lines (up to 3 lines)
//                    var foundLabel = false
//                    for (j in 1..3) { // Check the next 3 lines
//                        val nextIndex = i + j
//                        if (nextIndex >= processedLines.size) break
//                        val nextLine = processedLines[nextIndex]
//                        if (nextLine.contains("accettazione", ignoreCase = true) || labels["acceptanceTime"]!!.contains(nextLine, ignoreCase = true)) {
//                            labelList.add(Pair("acceptanceTime", nextIndex))
//                            foundLabel = true
//                            Log.d("OCRHelper", "Trovata Ora di accettazione (riga $nextIndex)")
//                            break
//                        } else if (nextLine.contains("completamento", ignoreCase = true) || labels["completionTime"]!!.contains(nextLine, ignoreCase = true)) {
//                            labelList.add(Pair("completionTime", nextIndex))
//                            foundLabel = true
//                            Log.d("OCRHelper", "Trovata Ora di completamento (riga $nextIndex)")
//                            break
//                        }
//                    }
//                    if (!foundLabel) {
//                        Log.w("OCRHelper", "Ora di trovata (riga $i), ma non seguita da accettazione o completamento nelle righe successive")
//                    }
//                    continue
//                }
//
//                // Detect other labels
//                if (line.contains(labels["deliveryFee"]!!, ignoreCase = true)) {
//                    labelList.add(Pair("deliveryFee", i))
//                    continue
//                }
//                if (line.contains(labels["totalEarnings"]!!, ignoreCase = true)) {
//                    labelList.add(Pair("totalEarnings", i))
//                    continue
//                }
//                if (line.contains(labels["tip"]!!, ignoreCase = true)) {
//                    labelList.add(Pair("tip", i))
//                    continue
//                }
//
//                // Collect currency values (keep duplicates for now)
//                val currencyMatcher = currencyPattern.matcher(line)
//                if (currencyMatcher.find()) {
//                    val valueStr = currencyMatcher.group(1)?.replace("[,.]".toRegex(), ".") ?: "0.0"
//                    val value = valueStr.toDoubleOrNull() ?: 0.0
//                    currencyList.add(Pair(value, i))
//                    Log.d("OCRHelper", "Valore monetario trovato (riga $i): $value")
//                    continue
//                }
//
//                // Collect time values
//                val timeMatcher = timePattern.matcher(line)
//                if (timeMatcher.find()) {
//                    val time = timeMatcher.group(1) ?: "00:00"
//                    timeList.add(Pair(time, i))
//                    Log.d("OCRHelper", "Ora trovata (riga $i): $time")
//                    continue
//                }
//            }
//
//            // Second pass: Match labels with times
//            var startTime: String? = null
//            var endTime: String? = null
//            val times = mutableListOf<Pair<Int, Int>>()
//
//            for (labelEntry in labelList) {
//                val label = labelEntry.first
//                val labelIndex = labelEntry.second
//
//                when (label) {
//                    "acceptanceTime" -> {
//                        val timeEntry = timeList.firstOrNull { it.second > labelIndex }
//                        if (timeEntry != null) {
//                            startTime = timeEntry.first
//                            Log.d("OCRHelper", "Ora di accettazione (riga ${timeEntry.second}): $startTime")
//                        }
//                    }
//                    "completionTime" -> {
//                        val timeEntry = timeList.firstOrNull { it.second > labelIndex }
//                        if (timeEntry != null) {
//                            endTime = timeEntry.first
//                            Log.d("OCRHelper", "Ora di completamento (riga ${timeEntry.second}): $endTime")
//                        }
//                    }
//                }
//            }
//
//            // Step 1: Find pagaTotale using "Consegna singola"
//            val singleDeliveryIndex = labelList.find { it.first == "singleDelivery" }?.second
//            if (singleDeliveryIndex != null) {
//                val totalEntry = currencyList.firstOrNull { it.second > singleDeliveryIndex }
//                if (totalEntry != null) {
//                    orderData.pagaTotale = totalEntry.first
//                    Log.d("OCRHelper", "PagaTotale identificata dopo Consegna singola (riga ${totalEntry.second}): ${orderData.pagaTotale}")
//                }
//            }
//
//            // If not found, fallback to the repeated value
//            if (orderData.pagaTotale == 0.0) {
//                val valueCounts = currencyList.groupBy { it.first }.mapValues { it.value.size }
//                val repeatedValueEntry = valueCounts.entries.find { it.value > 1 }
//                if (repeatedValueEntry != null) {
//                    orderData.pagaTotale = repeatedValueEntry.key
//                    Log.d("OCRHelper", "PagaTotale identificata (valore ripetuto): ${orderData.pagaTotale}")
//                } else {
//                    orderData.pagaTotale = currencyList.firstOrNull()?.first ?: 0.0
//                    Log.w("OCRHelper", "Nessun valore ripetuto trovato, uso il primo valore come PagaTotale: ${orderData.pagaTotale}")
//                }
//            }
//
//            // Step 2: Filter out the instances of pagaTotale and sort remaining values by line index
//            val remainingCurrencyList = currencyList
//                .filter { it.first != orderData.pagaTotale } // Remove all instances of pagaTotale
//                .sortedBy { it.second } // Sort by line index
//                .toMutableList() // Make it mutable so we can remove used values
//
//            // Step 3: Assign pagaBase FIRST using "deliveryFee" (Costo di consegna)
//            val deliveryFeeIndex = labelList.find { it.first == "deliveryFee" }?.second
//            if (deliveryFeeIndex != null) {
//                val baseValue = remainingCurrencyList.firstOrNull { it.second > deliveryFeeIndex }
//                if (baseValue != null) {
//                    orderData.pagaBase = baseValue.first
//                    Log.d("OCRHelper", "PagaBase identificata dopo Costo di consegna (riga ${baseValue.second}): ${orderData.pagaBase}")
//                    remainingCurrencyList.remove(baseValue) // Remove the used value
//                }
//            }
//
//            // Step 4: Fallback if pagaBase wasn't assigned
//            if (orderData.pagaBase == 0.0 && remainingCurrencyList.isNotEmpty()) {
//                val baseValue = remainingCurrencyList[0]
//                orderData.pagaBase = baseValue.first
//                Log.d("OCRHelper", "PagaBase identificata (primo valore unico dopo PagaTotale, riga ${baseValue.second}): ${orderData.pagaBase}")
//                remainingCurrencyList.remove(baseValue)
//            }
//
//            // Step 5: If no remaining values and pagaBase is still 0, assume pagaBase = pagaTotale (no tip)
//            if (orderData.pagaBase == 0.0) {
//                orderData.pagaBase = orderData.pagaTotale
//                Log.d("OCRHelper", "Nessun valore unico dopo PagaTotale, PagaBase impostata a PagaTotale: ${orderData.pagaBase}")
//            }
//
//            // Step 6: Assign mancia using "tip" (Mancia)
//            val tipIndex = labelList.find { it.first == "tip" }?.second
//            if (tipIndex != null) {
//                val tipValue = remainingCurrencyList.firstOrNull { it.second > tipIndex }
//                if (tipValue != null) {
//                    orderData.mancia = tipValue.first
//                    Log.d("OCRHelper", "Mancia identificata dopo Mancia (riga ${tipValue.second}): ${orderData.mancia}")
//                    remainingCurrencyList.remove(tipValue) // Remove the used value
//                }
//            }
//
//            // Step 7: Fallback for mancia if not assigned
//            if (orderData.mancia == 0.0) {
//                val expectedMancia = orderData.pagaTotale - orderData.pagaBase
//                if (expectedMancia > 0) {
//                    val potentialManciaEntry = remainingCurrencyList.firstOrNull()
//                    if (potentialManciaEntry != null && kotlin.math.abs(potentialManciaEntry.first - expectedMancia) < 0.01) { // Allow small floating-point differences
//                        orderData.mancia = potentialManciaEntry.first
//                        Log.d("OCRHelper", "Mancia identificata (riga ${potentialManciaEntry.second}): ${orderData.mancia}")
//                        remainingCurrencyList.remove(potentialManciaEntry)
//                    } else {
//                        orderData.mancia = expectedMancia
//                        Log.d("OCRHelper", "Mancia calcolata come PagaTotale - PagaBase: ${orderData.mancia}")
//                    }
//                }
//            }
//
//            // Validation: Ensure pagaTotale = pagaBase + pagaExtra + mancia
//            val calculatedTotal = orderData.pagaBase + orderData.pagaExtra + orderData.mancia
//            if (orderData.pagaTotale > 0 && kotlin.math.abs(orderData.pagaTotale - calculatedTotal) > 0.01) { // Allow small floating-point differences
//                Log.w("OCRHelper", "Validazione fallita: PagaTotale (${orderData.pagaTotale}) != PagaBase (${orderData.pagaBase}) + PagaExtra (${orderData.pagaExtra}) + Mancia (${orderData.mancia}) = $calculatedTotal")
//            } else {
//                Log.d("OCRHelper", "Validazione superata: PagaTotale (${orderData.pagaTotale}) = PagaBase (${orderData.pagaBase}) + PagaExtra (${orderData.pagaExtra}) + Mancia (${orderData.mancia})")
//            }
//
//            // Calculate time interval with improved handling for overnight orders
//            if (startTime != null && endTime != null) {
//                val startMinutes = timeToMinutes(startTime)
//                var endMinutes = timeToMinutes(endTime)
//                // If end time is earlier than start time, assume it’s the next day
//                if (endMinutes < startMinutes) {
//                    endMinutes += 1440 // Add 24 hours (in minutes)
//                    Log.d("OCRHelper", "Rilevato ordine notturno: aggiunto 1440 minuti a Ora di completamento")
//                }
//                times.add(Pair(startMinutes, endMinutes))
//                Log.d("OCRHelper", "Intervallo di tempo: $startTime - $endTime")
//            } else {
//                Log.w("OCRHelper", "Impossibile calcolare il tempo impiegato: Ora di accettazione ($startTime) o Ora di completamento ($endTime) non trovate")
//            }
//
//            if (times.isNotEmpty()) {
//                val earliestStart = times.minOf { it.first }
//                val latestEnd = times.maxOf { it.second }
//                orderData.tempoImpiegato = kotlin.math.abs(latestEnd - earliestStart)
//                Log.d("OCRHelper", "TempoImpiegato: ${orderData.tempoImpiegato} minuti")
//            }
//
//            orderData.numeroOrdini = ordersCount
//            Log.d("OCRHelper", "NumeroOrdini: ${orderData.numeroOrdini}")
//
//            if (orderData.tempoImpiegato > 0 && orderData.pagaTotale > 0) {
//                orderData.pagaOraria = (orderData.pagaTotale * 60) / orderData.tempoImpiegato
//                Log.d("OCRHelper", "PagaOraria: ${orderData.pagaOraria}")
//            } else {
//                Log.d("OCRHelper", "TempoImpiegato o PagaTotale = 0, PagaOraria non calcolata")
//            }
//
//            return orderData
//        }

        private fun minutesToSqlTime(totalMinutes: Int): java.sql.Time {
            val hours = (totalMinutes / 60) % 24 // Assicura che le ore siano nel range 0-23
            val minutes = totalMinutes % 60
            // Assicurati che l'import per LocalTime e DateTimeFormatter sia presente nel file OCRHelper.kt
            // import java.time.LocalTime
            // import java.time.format.DateTimeFormatter
            return java.sql.Time.valueOf(LocalTime.of(hours, minutes).format(DateTimeFormatter.ofPattern("HH:mm:ss")))
        }
        private fun parseGlovoText(visionText: Text, imageUri: Uri, context: Context, language: String = "it"): OrderData {
            val orderData = OrderData()
            orderData.providerID = 2 // Glovo

            // Inizializza il log
            val fileName = imageUri.lastPathSegment ?: ""
            LogHelper.initializeLogFile(fileName, context)
            LogHelper.log("OCRHelper", "fileName: $fileName")

            // Estrai la data
            orderData.data = extractDateFromUri(imageUri, context)
            LogHelper.log("OCRHelper", "Data estratta: ${orderData.data}")

            // Seleziona le etichette in base alla lingua
            val labelsForLanguage = glovoLabels[language] ?: glovoLabels["xx"] ?: glovoLabels["en"]!!



            val elements = mutableListOf<TextElement>()
            val textBlocks = visionText.textBlocks

            // Estrai elementi di testo
            for (block in textBlocks) {
                for (line in block.lines) {
                    val boundingBox = line.boundingBox
                    if (boundingBox != null) {
                        val x = boundingBox.left
                        val y = boundingBox.top
                        val width = boundingBox.width()
                        elements.add(TextElement(line.text, x, y, width))
                        LogHelper.log("OCRHelper", "Linea: ${line.text}, x: $x, y: $y, width: $width, xPlusWidth: ${x + width}")
                    }
                }
            }

            // Ordina elementi
            elements.sortWith(compareBy({ it.y }, { it.x }))

            // Calcola soglie dinamiche
            val yDistances = elements.map { it.y }.zipWithNext { a, b -> b - a }.filter { it > 0 }
            val yThreshold = if (yDistances.isNotEmpty()) minOf(yDistances.average().toInt() * 2, 200) else 200
            LogHelper.log("OCRHelper", "Soglia y dinamica: $yThreshold")

            val xPlusWidthValues = elements.map { it.xPlusWidth }
            val xPlusWidthThreshold = if (xPlusWidthValues.isNotEmpty()) xPlusWidthValues.average().toInt() else 600
            LogHelper.log("OCRHelper", "Soglia xPlusWidth dinamica per colonne: $xPlusWidthThreshold")

            // Regex
            val currencySymbol = CurrencyFormatter.getCurrencySymbol()
            val currencyPattern = getCurrencyPatterns(currencySymbol)[language] ?: getCurrencyPatterns(currencySymbol)["xx"]!!
            val doubleOrderPattern = Regex("""(\d+)\s*Consegne|Consegne\s*\((\d+)\)""", RegexOption.IGNORE_CASE)
            val distancePattern = Regex("(\\d+[,.]\\d*)\\s*km", RegexOption.IGNORE_CASE)

            // Determina il numero di ordini
            var numberOfOrders = 1 // Default: ordine singolo
            for (element in elements) {
                if (element.text.contains(labelsForLanguage["singleDelivery"]!!, ignoreCase = true)) {
                    numberOfOrders = 1
                    LogHelper.log("OCRHelper", "Rilevato ordine singolo: ${labelsForLanguage["singleDelivery"]}")
                    break
                } else if (doubleOrderPattern.containsMatchIn(element.text)) {
                    numberOfOrders = doubleOrderPattern.find(element.text)?.let { match ->
                        match.groupValues[1].toIntOrNull() ?: match.groupValues[2].toIntOrNull() ?: 2
                    } ?: 2
                    LogHelper.log("OCRHelper", "Rilevato ordine doppio: $numberOfOrders consegne")
                    break
                }
            }

            orderData.numeroOrdini = numberOfOrders
            LogHelper.log("OCRHelper", "NumeroOrdini: ${orderData.numeroOrdini}")

            // Delega il parsing
            return if (numberOfOrders > 1) {
                parseDoubleGlovoText(elements, orderData, context, language, labelsForLanguage, currencySymbol, currencyPattern, distancePattern, yThreshold, xPlusWidthThreshold)
            } else {
                parseSingleGlovoText(elements, orderData, context, language, labelsForLanguage, currencySymbol, currencyPattern, distancePattern, yThreshold, xPlusWidthThreshold)
            }
        }

        private fun parseSingleGlovoText(
            elements: List<TextElement>,
            orderData: OrderData,
            context: Context,
            language: String,
            labelsForLanguage: Map<String, String>,
            currencySymbol: String,
            currencyPattern: Regex,
            distancePattern: Regex,
            yThreshold: Int,
            xPlusWidthThreshold: Int
        ): OrderData {
            val labels = mutableListOf<Pair<String, TextElement>>()
            val values = mutableListOf<TextElement>()
            val timeElements = mutableListOf<TextElement>()

            // Raccogli etichette, valori, orari
            val timePattern = Regex("\\d{2}[:;]\\d{2}") // Per orari singoli
            for (element in elements) {
                // Raccogli orari
                if (timePattern.containsMatchIn(element.text)) {
                    timeElements.add(element)
                    LogHelper.log("OCRHelper", "Ora trovata: ${element.text}, x: ${element.x}, y: ${element.y}, xPlusWidth: ${element.xPlusWidth}")
                }

                // Raccogli valori valuta
                val currencyMatch = currencyPattern.find(element.text)
                if (currencyMatch != null) {
                    val cleanedValue = currencyMatch.value.replace(Regex("""\s+"""), "")
                    LogHelper.log("OCRHelper", "Valore trovato: $cleanedValue, x: ${element.x}, y: ${element.y}, xPlusWidth: ${element.xPlusWidth}")
                    values.add(element.copy(text = cleanedValue))
                }

                // Raccogli etichette
                when {
                    element.text.contains(labelsForLanguage["acceptanceTime"]!!, ignoreCase = true) -> {
                        labels.add("acceptanceTime" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["acceptanceTime"]}")
                    }
                    element.text.contains(labelsForLanguage["completionTime"]!!, ignoreCase = true) -> {
                        labels.add("completionTime" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["completionTime"]}")
                    }
                    element.text.contains(labelsForLanguage["totalEarnings"]!!, ignoreCase = true) -> {
                        labels.add("totalEarnings" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["totalEarnings"]}")
                    }
                    element.text.contains(labelsForLanguage["deliveryFee"]!!, ignoreCase = true) -> {
                        labels.add("deliveryFee" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["deliveryFee"]}")
                    }
                    element.text.contains(labelsForLanguage["tip"]!!, ignoreCase = true) -> {
                        labels.add("tip" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["tip"]}")
                    }
                    element.text.contains(labelsForLanguage["baseFee"]!!, ignoreCase = true) -> {
                        labels.add("baseFee" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["baseFee"]}")
                    }
                    element.text.contains(labelsForLanguage["distance"]!!, ignoreCase = true) && distancePattern.containsMatchIn(element.text) -> {
                        labels.add("distance" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["distance"]}")
                    }
                }
            }

            // Estrai il nome del ristorante
            val pickupPoint = elements.find { it.text.contains("Punto", ignoreCase = true) && it.text.contains("ritiro", ignoreCase = true) }
            if (pickupPoint != null) {
                val nextElement = elements.filter { it.y > pickupPoint.y }.minByOrNull { it.y }
                if (nextElement != null && nextElement.text.matches(Regex("[\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u017F][\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u017F0-9\\p{Punct}\\s]*")) && !nextElement.text.all { it.isDigit() }) {
                    orderData.ristorante = nextElement.text.trim()
                    LogHelper.log("OCRHelper", "Ristorante rilevato (Glovo singolo): ${orderData.ristorante}")
                } else {
                    LogHelper.log("OCRHelper", "Nessun ristorante valido dopo Punto di ritiro")
                }
            } else {
                LogHelper.log("OCRHelper", "Punto di ritiro non rilevato")
            }

            // Associa orari
            var startTime: String? = null
            var endTime: String? = null
            var tempoTotaleMinuti = 0
            val acceptanceLabel = labels.find { it.first == "acceptanceTime" }?.second
            val completionLabel = labels.find { it.first == "completionTime" }?.second

            if (acceptanceLabel != null) {
                val closestTime = timeElements.minByOrNull { time ->
                    val yDiff = abs(time.y - acceptanceLabel.y)
                    val xPlusWidthDiff = abs(time.xPlusWidth - acceptanceLabel.xPlusWidth)
                    val score = if (yDiff < yThreshold) yDiff + xPlusWidthDiff / 10 else Int.MAX_VALUE
                    LogHelper.log("OCRHelper", "Acceptance match: ${time.text}, yDiff: $yDiff, xPlusWidthDiff: $xPlusWidthDiff, score: $score")
                    score
                }
                if (closestTime != null) {
                    val timeText = closestTime.text.trim()
                    val timeMatch = timePattern.find(timeText)
                    startTime = timeMatch?.value?.takeIf { it.isNotBlank() }
                    LogHelper.log("OCRHelper", "Ora di accettazione associata: $startTime")
                    timeElements.remove(closestTime)
                }
            }

            if (completionLabel != null) {
                val closestTime = timeElements.minByOrNull { time ->
                    val yDiff = abs(time.y - completionLabel.y)
                    val xDiff = abs(time.x - completionLabel.x)
                    val score = if (yDiff < yThreshold) yDiff + xDiff / 10 else Int.MAX_VALUE
                    LogHelper.log("OCRHelper", "Completion match: ${time.text}, yDiff: $yDiff, xDiff: $xDiff, score: $score")
                    score
                }
                if (closestTime != null) {
                    val timeText = closestTime.text.trim()
                    val timeMatch = timePattern.find(timeText)
                    endTime = timeMatch?.value?.takeIf { it.isNotBlank() }
                    LogHelper.log("OCRHelper", "Ora di completamento associata: $endTime")
                    timeElements.remove(closestTime)
                }
            }

            if (startTime != null && endTime != null) {
                val startMinutes = timeToMinutes(startTime)
                var endMinutes = timeToMinutes(endTime)
                if (endMinutes < startMinutes) endMinutes += 24 * 60
                tempoTotaleMinuti = endMinutes - startMinutes
                LogHelper.log("OCRHelper", "TempoImpiegato (ordine singolo): $tempoTotaleMinuti minuti")
            } else {
                LogHelper.log("OCRHelper", "Errore: orari non validi per ordine singolo: startTime=$startTime, endTime=$endTime")
            }

            orderData.tempoImpiegato = tempoTotaleMinuti
            orderData.startTime = stringToSqlTime(startTime)
            orderData.endTime = stringToSqlTime(endTime)

            // Associa valori finanziari
            val financialLabels = labels.filter { it.first in listOf("totalEarnings", "deliveryFee", "tip", "baseFee", "distance") }
            val maxLabelXPlusWidth = financialLabels.maxOfOrNull { it.second.xPlusWidth } ?: 0
            LogHelper.log("OCRHelper", "Massimo xPlusWidth delle etichette finanziarie: $maxLabelXPlusWidth")

            val validValues = values.filter { it.x > maxLabelXPlusWidth }.toMutableList()
            LogHelper.log("OCRHelper", "Valori validi (x > $maxLabelXPlusWidth): ${validValues.map { it.text }}")

            val tipLabel = labels.find { it.first == "tip" }?.second
            if (tipLabel != null) {
                val closestValue = validValues.minByOrNull { v -> abs(v.y - tipLabel.y) }
                if (closestValue != null) {
                    val currencyMatch = currencyPattern.find(closestValue.text)
                    val value = currencyMatch?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    orderData.mancia = value
                    LogHelper.log("OCRHelper", "Mancia associata: $value")
                    validValues.remove(closestValue)
                }
            }

            for (labelEntry in financialLabels.filter { it.first != "tip" }) {
                val label = labelEntry.first
                val element = labelEntry.second
                val closestValue = validValues.minByOrNull { v -> abs(v.y - element.y) }
                if (closestValue != null) {
                    val currencyMatch = currencyPattern.find(closestValue.text)
                    val value = currencyMatch?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    LogHelper.log("OCRHelper", "Valore associato a $label: $value")
                    validValues.remove(closestValue)

                    when (label) {
                        "totalEarnings" -> orderData.pagaTotale = value
                        "deliveryFee" -> orderData.pagaBase = value
                        "baseFee" -> orderData.pagaBase = value
                    }
                }
            }

            // Gestione vecchio formato (Tariffa base + Distanza)
            val baseFeeLabel = labels.find { it.first == "baseFee" }?.second
            val distanceLabel = labels.find { it.first == "distance" }?.second
            if (baseFeeLabel != null && distanceLabel != null) {
                var baseFee: Double? = null
                var distanceFee: Double? = null

                val baseValue = validValues.minByOrNull { v -> abs(v.y - baseFeeLabel.y) }
                if (baseValue != null) {
                    baseFee = currencyPattern.find(baseValue.text)?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    validValues.remove(baseValue)
                }

                val distanceValue = validValues.minByOrNull { v -> abs(v.y - distanceLabel.y) }
                if (distanceValue != null) {
                    distanceFee = currencyPattern.find(distanceValue.text)?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    validValues.remove(distanceValue)
                }

                if (baseFee != null && distanceFee != null) {
                    orderData.pagaBase = baseFee + distanceFee
                    LogHelper.log("OCRHelper", "PagaBase calcolata (Tariffa base + Distanza): ${orderData.pagaBase}")
                }
            }

            // Minimo garantito
            if (orderData.pagaTotale < 3.30 && labels.none { it.first == "totalEarnings" }) {
                orderData.pagaTotale = 3.30
                orderData.pagaBase = 3.30
                LogHelper.log("OCRHelper", "Minimo garantito applicato: PagaTotale e PagaBase = 3.30")
            }

            // Validazione
            val calculatedTotal = orderData.pagaBase + orderData.mancia
            if (abs(orderData.pagaTotale - calculatedTotal) > 0.01) {
                LogHelper.log("OCRHelper", "Validazione fallita: PagaTotale (${orderData.pagaTotale}) != PagaBase (${orderData.pagaBase}) + Mancia (${orderData.mancia})")
                orderData.pagaBase = orderData.pagaTotale - orderData.mancia
                LogHelper.log("OCRHelper", "PagaBase ricalcolata: ${orderData.pagaBase}")
            }

            // Paga oraria
            if (tempoTotaleMinuti > 0) {
                orderData.pagaOraria = (orderData.pagaTotale * 60) / tempoTotaleMinuti
                LogHelper.log("OCRHelper", "PagaOraria: ${orderData.pagaOraria}")
            }

            LogHelper.closeLogFile()
            return orderData
        }

        // All'interno del companion object di OCRHelper
        private fun stringToSqlTime(timeString: String?): java.sql.Time {
            val defaultTimeValue = LocalTime.of(0, 0, 0)
            // Assicurati che DateTimeFormatter sia importato: import java.time.format.DateTimeFormatter
            // Assicurati che LocalTime sia importato: import java.time.LocalTime
            val defaultSqlTime = java.sql.Time.valueOf(defaultTimeValue.format(DateTimeFormatter.ofPattern("HH:mm:ss")))

            if (timeString == null) {
                LogHelper.log("OCRHelper", "Input timeString is null, returning default SqlTime.")
                return defaultSqlTime
            }
            return try {
                // Normalizza il separatore a ':' e parsa come LocalTime
                val cleanTimeString = timeString.replace(';', ':')
                val localTime = LocalTime.parse(cleanTimeString, DateTimeFormatter.ofPattern("HH:mm"))
                // java.sql.Time.valueOf necessita del formato "HH:mm:ss"
                java.sql.Time.valueOf(localTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            } catch (e: Exception) {
                LogHelper.log("OCRHelper", "Error parsing time string '$timeString' to SqlTime, returning default: ${e.message}")
                defaultSqlTime // Ritorna 00:00:00 se il parsing fallisce
            }
        }

        private fun parseDoubleGlovoText(
            elements: List<TextElement>,
            orderData: OrderData,
            context: Context,
            language: String,
            labelsForLanguage: Map<String, String>,
            currencySymbol: String,
            currencyPattern: Regex,
            distancePattern: Regex,
            yThreshold: Int,
            xPlusWidthThreshold: Int
        ): OrderData {
            val labels = mutableListOf<Pair<String, TextElement>>()
            val values = mutableListOf<TextElement>()
            val timeElements = mutableListOf<TextElement>()

            // Raccogli etichette, valori, orari
            val timePattern = Regex("(\\d{2}[:;]\\d{2})\\s*[-–]\\s*(\\d{2}[:;]\\d{2})")
            for (element in elements) {
                // Raccogli orari
                if (timePattern.containsMatchIn(element.text)) {
                    timeElements.add(element)
                    LogHelper.log("OCRHelper", "Ora trovata: ${element.text}, x: ${element.x}, y: ${element.y}, xPlusWidth: ${element.xPlusWidth}")
                }

                // Raccogli valori valuta
                val currencyMatch = currencyPattern.find(element.text)
                if (currencyMatch != null) {
                    val cleanedValue = currencyMatch.value.replace(Regex("""\s+"""), "")
                    LogHelper.log("OCRHelper", "Valore trovato: $cleanedValue, x: ${element.x}, y: ${element.y}, xPlusWidth: ${element.xPlusWidth}")
                    values.add(element.copy(text = cleanedValue))
                }

                // Raccogli etichette
                when {
                    element.text.contains(labelsForLanguage["totalEarnings"]!!, ignoreCase = true) -> {
                        labels.add("totalEarnings" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["totalEarnings"]}")
                    }
                    element.text.contains(labelsForLanguage["deliveryFee"]!!, ignoreCase = true) -> {
                        labels.add("deliveryFee" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["deliveryFee"]}")
                    }
                    element.text.contains(labelsForLanguage["tip"]!!, ignoreCase = true) -> {
                        labels.add("tip" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["tip"]}")
                    }
                    element.text.contains(labelsForLanguage["baseFee"]!!, ignoreCase = true) -> {
                        labels.add("baseFee" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["baseFee"]}")
                    }
                    element.text.contains(labelsForLanguage["distance"]!!, ignoreCase = true) && distancePattern.containsMatchIn(element.text) -> {
                        labels.add("distance" to element)
                        LogHelper.log("OCRHelper", "Etichetta trovata: ${labelsForLanguage["distance"]}")
                    }
                }
            }

            // Estrai il nome del ristorante
            val pickupPoint = elements.find { it.text.contains("Punto", ignoreCase = true) && it.text.contains("ritiro", ignoreCase = true) }
            if (pickupPoint != null) {
                val nextElement = elements.filter { it.y > pickupPoint.y }.minByOrNull { it.y }
                if (nextElement != null && nextElement.text.matches(Regex("[\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u017F][\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u017F0-9\\p{Punct}\\s]*")) && !nextElement.text.all { it.isDigit() }) {
                    orderData.ristorante = nextElement.text.trim()
                    LogHelper.log("OCRHelper", "Ristorante rilevato (Glovo doppio): ${orderData.ristorante}")
                } else {
                    LogHelper.log("OCRHelper", "Nessun ristorante valido dopo Punto di ritiro")
                }
            } else {
                LogHelper.log("OCRHelper", "Punto di ritiro non rilevato")
            }

            // Associa orari
            var startTime: String? = null
            var endTime: String? = null
            var tempoTotaleMinuti = 0
            val timeElement = timeElements.firstOrNull { timePattern.containsMatchIn(it.text) }
            if (timeElement != null) {
                val timeText = timeElement.text
                LogHelper.log("OCRHelper", "Intervallo temporale trovato: $timeText")
                val timeMatch = timePattern.find(timeText)
                if (timeMatch != null) {
                    startTime = timeMatch.groupValues[1].takeIf { it.isNotBlank() }
                    endTime = timeMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                    LogHelper.log("OCRHelper", "Orari estratti: startTime=$startTime, endTime=$endTime")
                    if (startTime != null && endTime != null) {
                        val startMinutes = timeToMinutes(startTime)
                        var endMinutes = timeToMinutes(endTime)
                        if (endMinutes < startMinutes) endMinutes += 24 * 60
                        tempoTotaleMinuti = endMinutes - startMinutes
                        LogHelper.log("OCRHelper", "TempoImpiegato (ordine doppio): $tempoTotaleMinuti minuti")
                    } else {
                        LogHelper.log("OCRHelper", "Errore: orari non validi in ordine doppio: startTime=$startTime, endTime=$endTime")
                    }
                } else {
                    // Fallback: split manuale
                    val parts = timeText.split("\\s*[-–]\\s*".toRegex()).filter { it.isNotBlank() }
                    if (parts.size == 2 && parts[0].matches("\\d{2}[:;]\\d{2}".toRegex()) && parts[1].matches("\\d{2}[:;]\\d{2}".toRegex())) {
                        startTime = parts[0]
                        endTime = parts[1]
                        LogHelper.log("OCRHelper", "Orari estratti (split manuale): startTime=$startTime, endTime=$endTime")
                        val startMinutes = timeToMinutes(startTime)
                        var endMinutes = timeToMinutes(endTime)
                        if (endMinutes < startMinutes) endMinutes += 24 * 60
                        tempoTotaleMinuti = endMinutes - startMinutes
                        LogHelper.log("OCRHelper", "TempoImpiegato (ordine doppio, split manuale): $tempoTotaleMinuti minuti")
                    } else {
                        LogHelper.log("OCRHelper", "Errore: formato intervallo non valido: $timeText")
                    }
                }
            } else {
                LogHelper.log("OCRHelper", "Errore: nessun intervallo temporale trovato per ordine doppio")
            }

            orderData.tempoImpiegato = tempoTotaleMinuti
            orderData.startTime = stringToSqlTime(startTime)
            orderData.endTime = stringToSqlTime(endTime)

            // Associa valori finanziari
            val financialLabels = labels.filter { it.first in listOf("totalEarnings", "deliveryFee", "tip", "baseFee", "distance") }
            val maxLabelXPlusWidth = financialLabels.maxOfOrNull { it.second.xPlusWidth } ?: 0
            LogHelper.log("OCRHelper", "Massimo xPlusWidth delle etichette finanziarie: $maxLabelXPlusWidth")

            val validValues = values.filter { it.x > maxLabelXPlusWidth }.toMutableList()
            LogHelper.log("OCRHelper", "Valori validi (x > $maxLabelXPlusWidth): ${validValues.map { it.text }}")

            val tipLabel = labels.find { it.first == "tip" }?.second
            if (tipLabel != null) {
                val closestValue = validValues.minByOrNull { v -> abs(v.y - tipLabel.y) }
                if (closestValue != null) {
                    val currencyMatch = currencyPattern.find(closestValue.text)
                    val value = currencyMatch?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    orderData.mancia = value
                    LogHelper.log("OCRHelper", "Mancia associata: $value")
                    validValues.remove(closestValue)
                }
            }

            for (labelEntry in financialLabels.filter { it.first != "tip" }) {
                val label = labelEntry.first
                val element = labelEntry.second
                val closestValue = validValues.minByOrNull { v -> abs(v.y - element.y) }
                if (closestValue != null) {
                    val currencyMatch = currencyPattern.find(closestValue.text)
                    val value = currencyMatch?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    LogHelper.log("OCRHelper", "Valore associato a $label: $value")
                    validValues.remove(closestValue)

                    when (label) {
                        "totalEarnings" -> orderData.pagaTotale = value
                        "deliveryFee" -> orderData.pagaBase = value
                        "baseFee" -> orderData.pagaBase = value
                    }
                }
            }

            // Gestione vecchio formato (Tariffa base + Distanza)
            val baseFeeLabel = labels.find { it.first == "baseFee" }?.second
            val distanceLabel = labels.find { it.first == "distance" }?.second
            if (baseFeeLabel != null && distanceLabel != null) {
                var baseFee: Double? = null
                var distanceFee: Double? = null

                val baseValue = validValues.minByOrNull { v -> abs(v.y - baseFeeLabel.y) }
                if (baseValue != null) {
                    baseFee = currencyPattern.find(baseValue.text)?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    validValues.remove(baseValue)
                }

                val distanceValue = validValues.minByOrNull { v -> abs(v.y - distanceLabel.y) }
                if (distanceValue != null) {
                    distanceFee = currencyPattern.find(distanceValue.text)?.groups?.get(1)?.value?.let { parseCurrency(it, currencySymbol) } ?: 0.0
                    validValues.remove(distanceValue)
                }

                if (baseFee != null && distanceFee != null) {
                    orderData.pagaBase = baseFee + distanceFee
                    LogHelper.log("OCRHelper", "PagaBase calcolata (Tariffa base + Distanza): ${orderData.pagaBase}")
                }
            }

            // Minimo garantito
            if (orderData.pagaTotale < 3.30 && labels.none { it.first == "totalEarnings" }) {
                orderData.pagaTotale = 3.30
                orderData.pagaBase = 3.30
                LogHelper.log("OCRHelper", "Minimo garantito applicato: PagaTotale e PagaBase = 3.30")
            }

            // Validazione
            val calculatedTotal = orderData.pagaBase + orderData.mancia
            if (abs(orderData.pagaTotale - calculatedTotal) > 0.01) {
                LogHelper.log("OCRHelper", "Validazione fallita: PagaTotale (${orderData.pagaTotale}) != PagaBase (${orderData.pagaBase}) + Mancia (${orderData.mancia})")
                orderData.pagaBase = orderData.pagaTotale - orderData.mancia
                LogHelper.log("OCRHelper", "PagaBase ricalcolata: ${orderData.pagaBase}")
            }

            // Paga oraria
            if (tempoTotaleMinuti > 0) {
                orderData.pagaOraria = (orderData.pagaTotale * 60) / tempoTotaleMinuti
                LogHelper.log("OCRHelper", "PagaOraria: ${orderData.pagaOraria}")
            }

            LogHelper.closeLogFile()
            return orderData
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

        private fun detectHorizontalLines(elements: List<TextElement>, yThreshold: Int): List<Int> {
            val lines = mutableListOf<Int>()
            for (element in elements) {
                val isLine = element.width > 100 && elements.none { it.y in (element.y - 10)..(element.y + 10) && it.x != element.x }
                if (isLine) {
                    lines.add(element.y)
                    LogHelper.log("OCRHelper", "Riga orizzontale rilevata a y: ${element.y}, width: ${element.width}")
                }
            }
            LogHelper.log("OCRHelper", "Linee orizzontali totali rilevate: ${lines.size} a y: ${lines.sorted()}")
            return lines.sorted()
        }
    }

    object LogHelper {
        private var logFile: File? = null
        private var logStream: FileOutputStream? = null

        fun initializeLogFile(fileName: String, context: Context) {
            try {
                val cleanFileName = fileName.substringAfterLast("/") // Remove path prefix
                val logDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "OCRLogs"
                )
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val logFileName = "${cleanFileName.replace(".jpg", "")}_OCRLog.txt"
                logFile = File(logDir, logFileName)
                logStream = FileOutputStream(logFile, true)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val header = "Log OCR - $timestamp\n\n"
                logStream?.write(header.toByteArray())
            } catch (e: Exception) {
                Log.e("LogHelper", "Errore durante l'inizializzazione del file di log: ${e.message}")
            }
        }

        fun log(tag: String, message: String) {
            // Scrivi sul log di sistema
            Log.d(tag, message)

            // Scrivi sul file
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val logMessage = "$timestamp $tag: $message\n"
                logStream?.write(logMessage.toByteArray())
            } catch (e: Exception) {
                Log.e("LogHelper", "Errore durante la scrittura del log su file: ${e.message}")
            }
        }

        fun closeLogFile() {
            try {
                logStream?.close()
                logStream = null
                logFile = null
            } catch (e: Exception) {
                Log.e("LogHelper", "Errore durante la chiusura del file di log: ${e.message}")
            }
        }
    }
}