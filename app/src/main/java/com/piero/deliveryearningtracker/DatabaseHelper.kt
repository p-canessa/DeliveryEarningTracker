package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.content.ContentValues
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import java.util.Calendar
import java.util.UUID

data class Totali(
    val totalPagaBase: Double,
    val totalPagaExtra: Double,
    val totalMancia: Double,
    val totalPagaTotale: Double,
    val totalNumeroOrdini: Int,
    val totalTempoImpiegato: Int,
    val totalPagaOraria: Double,
    val totalRiscossiContanti: Double,
    val totalManciaContanti: Double,
    val totaleContanti: Double
)

data class Ordine(
    val id: Long,
    val data: String,
    val pagaBase: Double,
    val pagaExtra: Double,
    val mancia: Double,
    val manciaContanti: Double,
    val riscossiContanti: Double,
    val numeroOrdini: Int,
    val tempoImpiegato: Int,
    val pagaTotale: Double,
    val pagaOraria: Double
)

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ordini.db"
        private const val DATABASE_VERSION = 4
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createProviderQuery = context.getString(R.string.Table_Providders)
            db.execSQL(createProviderQuery)
            val populateProvidersQuery = context.getString(R.string.Populate_Providers)
            db.execSQL(populateProvidersQuery)
            val createOrdiniTempTable = context.getString(R.string.Temporary_ordini)
            db.execSQL(createOrdiniTempTable)
            val copyOrdiniTemp = context.getString(R.string.Copy_ordini_Temp)
            db.execSQL(copyOrdiniTemp)
            val dropOrdini = context.getString(R.string.Drop_orddini)
            db.execSQL(dropOrdini)
            val renameOrdini = context.getString(R.string.Rename_ordini)
            db.execSQL(renameOrdini)
        }
        if (oldVersion < 3) {
            val createMonthlySummariesTable = context.getString(R.string.Table_MontlySummaries)
            db.execSQL(createMonthlySummariesTable)
            val createDailyOrdersTable = context.getString(R.string.Table_DailyOrders)
            db.execSQL(createDailyOrdersTable)
        }
        if (oldVersion < 4) {
            val createInviteCodeTable = context.getString(R.string.Table_InviteCode)
            db.execSQL(createInviteCodeTable)
            val createSubscriptionsTable = context.getString(R.string.Table_Subscriptions)
            db.execSQL(createSubscriptionsTable)
        }
    }

    fun initializeDatabase() {
        try {
            val dbPath = context.getDatabasePath(DATABASE_NAME)
            if (!dbPath.exists()) {
                Log.d("DatabaseHelper", "Database non trovato, creazione in corso...")
                dbPath.parentFile?.mkdirs()

                try {
                    val inputStream = context.assets.open(DATABASE_NAME)
                    val outputStream = FileOutputStream(dbPath)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    Log.d("DatabaseHelper", "Database copiato da assets.")
                } catch (e: IOException) {
                    Log.w("DatabaseHelper", "Database non trovato in assets, creazione di uno nuovo...")
                    val db = writableDatabase
                    createAllTables(db)
                    db.close()
                }
            }
            // Verifica e crea tabelle mancanti in ogni caso
            val db = writableDatabase
            ensureTablesExist(db)
            db.close()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Errore durante l'inizializzazione del database: ${e.message}", e)
        }
    }

    private fun createAllTables(db: SQLiteDatabase) {
        val createTableQuery = context.getString(R.string.Table_Ordini)
        db.execSQL(createTableQuery)
        Log.d("DatabaseHelper", "Tabella 'ordini' creata o già esistente.")

        val createProviderQuery = context.getString(R.string.Table_Providders)
        db.execSQL(createProviderQuery)
        val populateProvidersQuery = context.getString(R.string.Populate_Providers)
        db.execSQL(populateProvidersQuery)

        val createMonthlySummariesTable = context.getString(R.string.Table_MontlySummaries)
        db.execSQL(createMonthlySummariesTable)

        val createDailyOrdersTable = context.getString(R.string.Table_DailyOrders)
        db.execSQL(createDailyOrdersTable)

        val createInviteCodeTable = context.getString(R.string.Table_InviteCode)
        db.execSQL(createInviteCodeTable)

        val createSubscriptionsTable = context.getString(R.string.Table_Subscriptions)
        db.execSQL(createSubscriptionsTable)
    }

    private fun ensureTablesExist(db: SQLiteDatabase) {
        val tables = listOf(
            "ordini" to context.getString(R.string.Table_Ordini),
            "Providers" to context.getString(R.string.Table_Providders),
            "MonthlySummaries" to context.getString(R.string.Table_MontlySummaries),
            "DailyOrders" to context.getString(R.string.Table_DailyOrders),
            "InviteCode" to context.getString(R.string.Table_InviteCode),
            "Subscriptions" to context.getString(R.string.Table_Subscriptions)
        )

        for ((tableName, createQuery) in tables) {
            if (!tableExists(db, tableName)) {
                db.execSQL(createQuery)
                Log.d("DatabaseHelper", "Tabella '$tableName' creata.")
            }
        }

        if (tableExists(db, "Providers")) {
            val countQuery = "SELECT COUNT(*) FROM Providers"
            val cursor = db.rawQuery(countQuery, null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            if (count == 0) {
                val populateProvidersQuery = context.getString(R.string.Populate_Providers)
                db.execSQL(populateProvidersQuery)
                Log.d("DatabaseHelper", "Tabella 'Providers' popolata.")
            }
        }
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getInviteCode(): String {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT code FROM InviteCode LIMIT 1", null)
        return if (cursor.moveToFirst()) {
            cursor.getString(0)
        } else {
            val newCode = "rider${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
            val values = ContentValues().apply {
                put("code", newCode)
            }
            db.insert("InviteCode", null, values)
            newCode
        }.also { cursor.close() }
    }

    fun insertSubscription(days: Int) {
        val db = writableDatabase
        val calendar = Calendar.getInstance()
        val startDate = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, days)
        val endDate = calendar.timeInMillis

        val values = ContentValues().apply {
            put("start_date", startDate)
            put("end_date", endDate)
            put("active", 1)
        }
        db.insert("Subscriptions", null, values)
        db.close()

        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPref.edit { putBoolean("ads_enabled", false) }
    }

    private fun isSubscriptionActive(): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT end_date FROM Subscriptions WHERE active = 1 AND end_date > ?",
            arrayOf(System.currentTimeMillis().toString())
        )
        val isActive = cursor.moveToFirst()
        cursor.close()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        val isAdsEnabled = sharedPref.getBoolean("ads_enabled", true)
        return isActive || !isAdsEnabled
    }

    fun updateAdsEnabledState() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        if (isSubscriptionActive()) {
            sharedPref.edit { putBoolean("ads_enabled", false) }
        }
    }


    // Sovrascrivi getReadableDatabase per gestire errori
    override fun getReadableDatabase(): SQLiteDatabase {
        return try {
            super.getReadableDatabase()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Errore durante l'apertura del database in lettura: ${e.message}", e)
            // Forza la creazione del database
            initializeDatabase()
            super.getReadableDatabase()
        }
    }

    // Sovrascrivi getWritableDatabase per gestire errori
    override fun getWritableDatabase(): SQLiteDatabase {
        return try {
            super.getWritableDatabase()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Errore durante l'apertura del database in scrittura: ${e.message}", e)
            // Forza la creazione del database
            initializeDatabase()
            super.getWritableDatabase()
        }
    }
    fun insertOrder(order: OrderData) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Data", order.data)
            put("ProviderID",order.providerID)
            put("PagaBase", order.pagaBase)
            put("RiscossiContanti", order.riscossiContanti)
            put("PagaExtra", order.pagaExtra)
            put("Mancia", order.mancia + order.manciaContanti)
            put("ManciaContanti", order.manciaContanti)
            put("NumeroOrdini", order.numeroOrdini)
            put("TempoImpiegato", order.tempoImpiegato)
            put("PagaTotale", order.pagaTotale)
            put("PagaOraria", order.pagaOraria)
        }

        Log.d("DatabaseHelper", values.toString())

        db.insert("ordini", null, values)
        db.close()
    }

    fun getTotali(sqlClause: String): Totali? {
        val db = readableDatabase
        val query = """
        SELECT 
            SUM(PagaBase) as totalPagaBase,
            SUM(PagaExtra) as totalPagaExtra,
            SUM(Mancia) as totalMancia,
            SUM(PagaTotale) as totalPagaTotale,
            SUM(NumeroOrdini) as totalNumeroOrdini,
            SUM(TempoImpiegato) as totalTempoImpiegato,
            CASE 
                WHEN SUM(TempoImpiegato) = 0 THEN 0.0
                ELSE (SUM(PagaTotale) / (SUM(TempoImpiegato) / 60.0)) 
            END as totalPagaOraria,
            SUM(RiscossiContanti) as totalRiscossiContanti,
            SUM(ManciaContanti) as totalManciaContanti,
            SUM(RiscossiContanti + ManciaContanti) as totaleContanti
        FROM ordini
        $sqlClause
    """
        val cursor = db.rawQuery(query, null)
        return if (cursor.moveToFirst()) {
            val totali = Totali(
                totalPagaBase = cursor.getDouble(0),
                totalPagaExtra = cursor.getDouble(1),
                totalMancia = cursor.getDouble(2),
                totalPagaTotale = cursor.getDouble(3),
                totalNumeroOrdini = cursor.getInt(4),
                totalTempoImpiegato = cursor.getInt(5),
                totalPagaOraria = cursor.getDouble(6),
                totalRiscossiContanti = cursor.getDouble(7),
                totalManciaContanti = cursor.getDouble(8),
                totaleContanti = cursor.getDouble(9)
            )
            cursor.close()
            totali
        } else {
            cursor.close()
            null
        }
    }

    fun getOrdineById(id: Long): Ordine? {
        val db = readableDatabase
        val cursor = db.query(
            "ordini", null, "ID = ?", arrayOf(id.toString()), null, null, null
        )
        return if (cursor.moveToFirst()) {
            val ordine = Ordine(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("ID")),
                data = cursor.getString(cursor.getColumnIndexOrThrow("Data")),
                pagaBase = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaBase")),
                pagaExtra = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaExtra")),
                mancia = cursor.getDouble(cursor.getColumnIndexOrThrow("Mancia")),
                manciaContanti = cursor.getDouble(cursor.getColumnIndexOrThrow("ManciaContanti")),
                riscossiContanti = cursor.getDouble(cursor.getColumnIndexOrThrow("RiscossiContanti")),
                numeroOrdini = cursor.getInt(cursor.getColumnIndexOrThrow("NumeroOrdini")),
                tempoImpiegato = cursor.getInt(cursor.getColumnIndexOrThrow("TempoImpiegato")),
                pagaTotale = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaTotale")),
                pagaOraria = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaOraria"))
            )
            cursor.close()
            ordine
        } else {
            cursor.close()
            null
        }
    }

    fun deleteOrdine(id: Long) {
        val db = writableDatabase
        db.delete("ordini", "ID = ?", arrayOf(id.toString()))
    }

    fun saveOrdine(ordine: Ordine): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Data", ordine.data)
            put("ProviderID", 1) // Fisso a 1 per ora, come nel codice originale
            put("PagaBase", ordine.pagaBase)
            put("PagaExtra", ordine.pagaExtra)
            put("Mancia", ordine.mancia)
            put("ManciaContanti", ordine.manciaContanti)
            put("RiscossiContanti", ordine.riscossiContanti)
            put("NumeroOrdini", ordine.numeroOrdini)
            put("TempoImpiegato", ordine.tempoImpiegato)
            put("PagaTotale", ordine.pagaTotale)
            put("PagaOraria", ordine.pagaOraria)
        }

        return if (ordine.id == -1L) {
            // Nuovo ordine, inserisci
            db.insert("ordini", null, values)
        } else {
            // Ordine esistente, aggiorna
            db.update("ordini", values, "ID = ?", arrayOf(ordine.id.toString()))
            ordine.id
        }
    }

    fun getOrderIds(sqlClause: String): List<Long> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT ID FROM ordini $sqlClause", null)
        val orderIds = mutableListOf<Long>()
        while (cursor.moveToNext()) {
            orderIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("ID")))
        }
        cursor.close()
        return orderIds
    }

    /*fun getOrderSummaryByDate(sqlClause: String): Pair<Int, Double> {
        val db = readableDatabase
        val sqlQuery="""SELECT count(ID) As numeroOrdini, Sum(PagaBase + PagaExtra) as totalPaga 
            FROM ordini
            WHERE Data = '$sqlClause'"""
        val cursor = db.rawQuery(sqlQuery, null)
        var nOrdini = 0
        var pTot = 0.0
        if(cursor.moveToFirst()) {
            nOrdini = cursor.getInt(cursor.getColumnIndexOrThrow("numeroOrdini"))
            pTot = cursor.getDouble(cursor.getColumnIndexOrThrow("totalPaga"))
            Log.d("DatabaseHelper", "$sqlClause - $nOrdini - $pTot")
        }
        cursor.close()
        return Pair(nOrdini, pTot)
    }*/

    // Inserisci un riepilogo mensile e restituisci l'ID
    fun insertMonthlySummary(summary: RiepilogoData): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("document_number", summary.documentNumber)
            put("month", summary.month) // Mantieni per compatibilità, se necessario
            put("month_number", summary.monthNumber) // Aggiungi questo
            put("year", summary.year) // Aggiungi questo
            put("tax_regime", summary.taxRegime)
            put("ordini_lordo", summary.ordiniLordo)
            put("ordini_ritenuta_acconto", summary.ordiniRitenutaAcconto)
            put("ordini_importo_ritenuta", summary.ordiniImportoRitenuta)
            put("ordini_iva", summary.ordiniIva)
            put("ordini_importo_iva", summary.ordiniImportoIva)
            put("ordini_totale", summary.ordiniTotale)
            put("integrazioni_lordo", summary.integrazioniLordo)
            put("integrazioni_ritenuta_acconto", summary.integrazioniRitenutaAcconto)
            put("integrazioni_importo_ritenuta", summary.integrazioniImportoRitenuta)
            put("integrazioni_iva", summary.integrazioniIva)
            put("integrazioni_importo_iva", summary.integrazioniImportoIva)
            put("integrazioni_totale", summary.integrazioniTotale)
            put("mance_lordo", summary.manceLordo)
            put("mance_ritenuta_acconto", summary.manceRitenutaAcconto)
            put("mance_importo_ritenuta", summary.manceImportoRitenuta)
            put("mance_iva", summary.manceIva)
            put("mance_importo_iva", summary.manceImportoIva)
            put("mance_totale", summary.manceTotale)
            put("totale_lordo", summary.totaleLordo)
            put("totale_ritenuta_acconto", summary.totaleRitenutaAcconto)
            put("totale_importo_ritenuta", summary.totaleImportoRitenuta)
            put("totale_iva", summary.totaleIva)
            put("totale_importo_iva", summary.totaleImportoIva)
            put("totale_totale", summary.totaleTotale)
            put("pagamenti_contanti", summary.pagamentiContanti)
            put("totale_dovuto", summary.totaleDovuto)
        }
        val id = db.insert("MonthlySummaries", null, values)
        db.close()
        return id
    }

    // Inserisci gli ordini giornalieri
    fun insertDailyOrders(orders: List<GuadagniGiornalieri>, monthlySummaryId: Long) {
        val db = writableDatabase
        for (order in orders) {
            val values = ContentValues().apply {
                put("monthly_summary_id", monthlySummaryId)
                put("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(order.data))
                put("number_of_orders", order.numeroOrdini)
                put("total_gross", order.totaleLordo)
            }
            db.insert("DailyOrders", null, values)
        }
        db.close()
    }

    fun isDocumentNumberExists(documentNumber: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM MonthlySummaries WHERE document_number = ?",
            arrayOf(documentNumber)
        )
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count > 0
    }

    fun getMonthlySummary(id: Int): RiepilogoData {
        val db = readableDatabase
        val cursor = db.query("MonthlySummaries", null, "id = ?", arrayOf(id.toString()), null, null, null)
        return if (cursor.moveToFirst()) {
            val rieplogo = RiepilogoData (
                //id =cursor.getInt(0),
                documentNumber = cursor.getString(1),
                month = cursor.getString(2),
                monthNumber = cursor.getInt(3),
                year = cursor.getInt(4),
                taxRegime = cursor.getString(5),
                ordiniLordo = cursor.getDouble(6),
                ordiniRitenutaAcconto = cursor.getString(7),
                ordiniImportoRitenuta = cursor.getDouble(8),
                ordiniIva = cursor.getString(9),
                ordiniImportoIva = cursor.getDouble(10),
                ordiniTotale = cursor.getDouble(11),
                integrazioniLordo = cursor.getDouble(12),
                integrazioniRitenutaAcconto = cursor.getString(13),
                integrazioniImportoRitenuta = cursor.getDouble(14),
                integrazioniIva = cursor.getString(15),
                integrazioniImportoIva = cursor.getDouble(16),
                integrazioniTotale = cursor.getDouble(17),
                manceLordo = cursor.getDouble(18),
                manceRitenutaAcconto = cursor.getString(19),
                manceImportoRitenuta = cursor.getDouble(20),
                manceIva = cursor.getString(21),
                manceImportoIva = cursor.getDouble(22),
                manceTotale = cursor.getDouble(23),
                totaleLordo = cursor.getDouble(24),
                totaleRitenutaAcconto = cursor.getString(25),
                totaleImportoRitenuta = cursor.getDouble(26),
                totaleIva = cursor.getString(27),
                totaleImportoIva = cursor.getDouble(28),
                totaleTotale = cursor.getDouble(29),
                pagamentiContanti = cursor.getDouble(30),
                totaleDovuto = cursor.getDouble(31)
            )
            cursor.close()
            rieplogo
        } else {
            cursor.close()
            return RiepilogoData(
                "",
                0,
                0,
                "",
                "",
                0.0,
                null,
                null,
                null,
                null,
                0.0,
                0.0,
                null,
                null,
                null,
                null,
                0.0,
                0.0,
                null,
                null,
                null,
                null,
                0.0,
                0.0,
                null,
                null,
                null,
                null,
                0.0,
                null,
                null)
        }
    }

    @SuppressLint("Range")
    fun getDailyOrders(summaryId: Int): List<DailyOrder> {
        val db = readableDatabase
        val columns = arrayOf("date", "number_of_orders", "total_gross")
        val cursor = db.query(
            "DailyOrders",
            columns,
            "monthly_summary_id = ?",
            arrayOf(summaryId.toString()),
            null,
            null,
            "date ASC"
        )
        val orders = mutableListOf<DailyOrder>()
        if (cursor.moveToFirst()) {
            do {
                val date = cursor.getString(cursor.getColumnIndexOrThrow("date"))
                val numberOfOrders = cursor.getInt(cursor.getColumnIndexOrThrow("number_of_orders"))
                val totalGross = cursor.getDouble(cursor.getColumnIndexOrThrow("total_gross"))
                orders.add(DailyOrder(date, numberOfOrders, totalGross))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return orders
    }

    fun getPreviousSummaryId(currentId: Int): Int {
        val db = readableDatabase

        // Ottieni year e month_number del riepilogo corrente
        val currentCursor = db.rawQuery(
            "SELECT year, month_number FROM MonthlySummaries WHERE id = ?",
            arrayOf(currentId.toString())
        )
        if (!currentCursor.moveToFirst()) {
            currentCursor.close()
            return -1 // Se l'ID corrente non esiste, restituisci -1
        }
        val currentYear = currentCursor.getInt(0)
        val currentMonthNumber = currentCursor.getInt(1)
        currentCursor.close()

        // Query per trovare il riepilogo precedente
        val cursor = db.rawQuery("""
        SELECT id
        FROM MonthlySummaries
        WHERE (year < ? OR (year = ? AND month_number < ?))
        ORDER BY year DESC, month_number DESC, id DESC
        LIMIT 1
    """.trimIndent(), arrayOf(
            currentYear.toString(),
            currentYear.toString(),
            currentMonthNumber.toString()
        ))

        return if (cursor.moveToFirst()) {
            val prevId = cursor.getInt(0)
            cursor.close()
            prevId
        } else {
            cursor.close()
            -1
        }
    }

    fun getNextSummaryId(currentId: Int): Int {
        val db = readableDatabase

        // Ottieni year e month_number del riepilogo corrente
        val currentCursor = db.rawQuery(
            "SELECT year, month_number FROM MonthlySummaries WHERE id = ?",
            arrayOf(currentId.toString())
        )
        if (!currentCursor.moveToFirst()) {
            currentCursor.close()
            return -1 // Se l'ID corrente non esiste, restituisci -1
        }
        val currentYear = currentCursor.getInt(0)
        val currentMonthNumber = currentCursor.getInt(1)
        currentCursor.close()

        // Query per trovare il riepilogo successivo
        val cursor = db.rawQuery("""
        SELECT id
        FROM MonthlySummaries
        WHERE (year > ? OR (year = ? AND month_number > ?))
        ORDER BY year ASC, month_number ASC, id ASC
        LIMIT 1
    """.trimIndent(), arrayOf(
            currentYear.toString(),
            currentYear.toString(),
            currentMonthNumber.toString()
        ))

        return if (cursor.moveToFirst()) {
            val nextId = cursor.getInt(0)
            cursor.close()
            nextId
        } else {
            cursor.close()
            -1
        }
    }

    fun getLatestSummaryId(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("""
        SELECT id
        FROM MonthlySummaries
        ORDER BY year DESC, month_number DESC, id DESC
        LIMIT 1
    """.trimIndent(), null)

        return if (cursor.moveToFirst()) {
            val id = cursor.getInt(0)
            cursor.close()
            id
        } else {
            cursor.close()
            0
        }
    }

    fun getTrimestreData(months: List<Int>, year: Int): Map<String, Double> {
        val db = readableDatabase
        val query = """
        SELECT 
            SUM(ordini_lordo) AS ordini_lordo,
            SUM(ordini_totale) AS ordini_netto,
            SUM(ordini_importo_iva) AS ordini_iva,
            SUM(integrazioni_lordo) AS integrazioni_lordo,
            SUM(integrazioni_totale) AS integrazioni_netto,
            SUM(integrazioni_importo_iva) AS integrazioni_iva,
            SUM(mance_lordo) AS mance_lordo,
            SUM(mance_totale) AS mance_netto,
            SUM(mance_importo_iva) AS mance_iva,
            SUM(totale_lordo) AS totale_lordo,
            SUM(totale_totale) AS totale_netto,
            SUM(totale_importo_iva) AS totale_iva
        FROM MonthlySummaries
        WHERE year = ? AND month_number IN (${months.joinToString(",")})
    """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(year.toString()))
        val data = mutableMapOf<String, Double>()

        if (cursor.moveToFirst()) {
            data["ordini_lordo"] = cursor.getDouble(0)
            data["ordini_netto"] = cursor.getDouble(1)
            data["ordini_iva"] = cursor.getDouble(2)
            data["integrazioni_lordo"] = cursor.getDouble(3)
            data["integrazioni_netto"] = cursor.getDouble(4)
            data["integrazioni_iva"] = cursor.getDouble(5)
            data["mance_lordo"] = cursor.getDouble(6)
            data["mance_netto"] = cursor.getDouble(7)
            data["mance_iva"] = cursor.getDouble(8)
            data["totale_lordo"] = cursor.getDouble(9)
            data["totale_netto"] = cursor.getDouble(10)
            data["totale_iva"] = cursor.getDouble(11)
        }

        cursor.close()
        return data
    }

    fun getAnnualData(year: String): DatiAggregati {
        val db = readableDatabase
        val query = """
        SELECT 
            SUM(ordini_lordo) AS ordini_lordo,
            SUM(ordini_totale) AS ordini_netto,
            SUM(ordini_importo_iva) AS ordini_iva,
            SUM(integrazioni_lordo) AS integrazioni_lordo,
            SUM(integrazioni_totale) AS integrazioni_netto,
            SUM(integrazioni_importo_iva) AS integrazioni_iva,
            SUM(mance_lordo) AS mance_lordo,
            SUM(mance_totale) AS mance_netto,
            SUM(mance_importo_iva) AS mance_iva,
            SUM(totale_lordo) AS totale_lordo,
            SUM(totale_totale) AS totale_netto,
            SUM(totale_importo_iva) AS totale_iva
        FROM MonthlySummaries
        WHERE 
            (year = ? AND month_number = 12) OR  -- Dicembre dell'anno precedente
            (year = ? AND month_number <= 11)    -- Gennaio a novembre dell'anno corrente
    """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf((year.toInt()-1).toString(), year))
        val data = mutableMapOf<String, Double>()

        if (cursor.moveToFirst()) {
            data["ordini_lordo"] = cursor.getDouble(0)
            data["ordini_netto"] = cursor.getDouble(1)
            data["ordini_iva"] = cursor.getDouble(2)
            data["integrazioni_lordo"] = cursor.getDouble(3)
            data["integrazioni_netto"] = cursor.getDouble(4)
            data["integrazioni_iva"] = cursor.getDouble(5)
            data["mance_lordo"] = cursor.getDouble(6)
            data["mance_netto"] = cursor.getDouble(7)
            data["mance_iva"] = cursor.getDouble(8)
            data["totale_lordo"] = cursor.getDouble(9)
            data["totale_netto"] = cursor.getDouble(10)
            data["totale_iva"] = cursor.getDouble(11)
        }

        cursor.close()
        // Numero totale di consegne
        val queryConsegne = """
        SELECT SUM(NumeroOrdini) AS total_consegne
        FROM ordini
        WHERE strftime('%Y', Data) = ?
    """.trimIndent()
        val cursorConsegne = db.rawQuery(queryConsegne, arrayOf(year))
        val totalConsegne = if (cursorConsegne.moveToFirst()) cursorConsegne.getInt(0) else 0
        cursorConsegne.close()

        return DatiAggregati(data,totalConsegne)
    }

    data class DatiAggregati(
        val data: Map<String, Double>,
        val consegne: Int
    )

    fun getYears(): Collection <Int> {
        val db = readableDatabase
        val query = """
            SELECT DISTINCT year 
            FROM MonthlySummaries 
            ORDER BY year DESC
        """.trimIndent()
        val cursor = db.rawQuery(query,null)
        val years = mutableListOf<Int>()

        cursor.use { cursr ->
            if (cursr.moveToFirst()) {
                do {
                    val year = cursr.getInt(0)  // Estrae il valore della colonna 'year'
                    years.add(year)              // Aggiunge il valore alla lista
                } while (cursr.moveToNext())
            }
        }

        return years
    }

    fun getDailyReconciliationData(month: String, year: Int): List<DailyReconciliation> {
        val db = readableDatabase
        val query = """
        SELECT 
        do.date,
        COALESCE(do.total_gross, 0) AS daily_total_gross,
        COALESCE(SUM(o.PagaBase + o.PagaExtra), 0) AS orders_total_gross,
        COALESCE(do.number_of_orders, 0) AS daily_number_of_orders,
        COALESCE(COUNT(o.ID), 0) AS orders_count
        FROM DailyOrders do
        LEFT JOIN ordini o ON strftime('%Y-%m-%d', o.Data) = do.date
        WHERE strftime('%Y', do.date) = ? AND strftime('%m', do.date) = ?
        GROUP BY do.date
        
        UNION
        
        SELECT 
            strftime('%Y-%m-%d', o.Data) AS date,
            0 AS daily_total_gross,
            SUM(o.PagaBase + o.PagaExtra) AS orders_total_gross,
            0 AS daily_number_of_orders,
            COUNT(o.ID) AS orders_count
        FROM ordini o
        WHERE strftime('%Y', o.Data) = ? AND strftime('%m', o.Data) = ?
        AND NOT EXISTS (
            SELECT 1 FROM DailyOrders do WHERE do.date = strftime('%Y-%m-%d', o.Data)
        )
        GROUP BY strftime('%Y-%m-%d', o.Data)
    """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(year.toString(), month))
        val data = mutableListOf<DailyReconciliation>()
        if (cursor.moveToFirst()) {
            do {
                data.add(
                    DailyReconciliation(
                        date = cursor.getString(0),
                        dailyTotalGross = cursor.getDouble(1),
                        ordersTotalGross = cursor.getDouble(2),
                        dailyNumberOfOrders = cursor.getInt(3),
                        ordersCount = cursor.getInt(4)
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return data
    }

    fun getMonthlyReconciliationData(month: String, year: Int): MonthlyReconciliation {
        val db = readableDatabase
        val query = """
        SELECT 
            ms.mance_lordo,
            SUM(o.Mancia - o.ManciaContanti) AS orders_mancia,
            ABS(ms.pagamenti_contanti) as pagamenti_contanti,
            SUM(o.RiscossiContanti) AS orders_riscossi_contanti
        FROM MonthlySummaries ms
        LEFT JOIN ordini o ON strftime('%Y-%m', o.Data) = ?
        WHERE ms.month_number = ? AND ms.year = ?
        GROUP BY ms.id
    """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf("$year-$month", month, year.toString()))
        val data = if (cursor.moveToFirst()) {
            MonthlyReconciliation(
                manceLordo = cursor.getDouble(0),
                ordersMancia = cursor.getDouble(1),
                pagamentiContanti = cursor.getDouble(2),
                ordersRiscossiContanti = cursor.getDouble(3)
            )
        } else {
            MonthlyReconciliation(0.0, 0.0, 0.0, 0.0)
        }
        cursor.close()
        return data
    }



    data class DailyReconciliation(
        val date: String,
        val dailyTotalGross: Double,
        val ordersTotalGross: Double,
        val dailyNumberOfOrders: Int,
        val ordersCount: Int
    )

    data class MonthlyReconciliation(
        val manceLordo: Double,
        val ordersMancia: Double,
        val pagamentiContanti: Double,
        val ordersRiscossiContanti: Double
    )
}