package com.piero.deliveryearningtracker

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.piero.deliveryearningtracker.utils.TimeUtils
import java.io.FileOutputStream
import java.io.IOException
import java.sql.Time
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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

/*data class Ordine(
    val id: Long,
    val data: String,
    val providerId: Int, // Aggiungi questo campo
    val pagaBase: Double,
    val pagaExtra: Double,
    val mancia: Double,
    val manciaContanti: Double,
    val riscossiContanti: Double,
    val numeroOrdini: Int,
    val tempoImpiegato: Int,
    val pagaTotale: Double,
    val pagaOraria: Double
)*/

data class CandidateOrder(
    val ID: Long,
    val ProviderName: String,
    val Ristorante: String,
    val StartTime: Time,
    val EndTime: Time,
    val PagaTotale: Double,
    val OrderStrategy: Int,
    val BatchMasterOrderID: Long?,
    val TempoImpiegato: Int
)

data class Order(val id: Long, val providerId: Int, val providerName: String)

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ordini.db"
        private const val DATABASE_VERSION = 8
        private var instance: DatabaseHelper? = null
        fun getInstance(context: Context): DatabaseHelper {
            if (instance == null) {
                instance = DatabaseHelper(context.applicationContext)
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
    }

    init {
        Log.d("DatabaseHelper", "DatabaseHelper inizializzato con DATABASE_VERSION=$DATABASE_VERSION")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("DatabaseHelper", "Eseguo upgrade da versione $oldVersion a $newVersion")
        if (oldVersion < 2) {
            val createProviderQuery = context.getString(R.string.Table_Providders)
            db.execSQL(createProviderQuery)
            populateProviders(db)
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
        }
        if (oldVersion < 5) {
            val createProviderQuery = "Drop table if exists Subscriptions"
            db.execSQL(createProviderQuery)
        }
        if (oldVersion < 8) {
            try {
                Log.d("DatabaseHelper", "Eseguo upgrade per versione < 8")
                // Aggiungi le colonne solo se non esistono già (buona pratica, anche se ALTER TABLE di solito non fallisce se esistono)
                if (!columnExists(db, "ordini", "StartTime")) {
                    db.execSQL("ALTER TABLE ordini ADD COLUMN StartTime TEXT DEFAULT '00:00:00'")
                }
                if (!columnExists(db, "ordini", "EndTime")) {
                    db.execSQL("ALTER TABLE ordini ADD COLUMN EndTime TEXT DEFAULT '00:00:00'")
                }
                if (!columnExists(db, "ordini", "OrderStrategy")) {
                    // Assicurati che OrderStrategyConstants.NORMAL sia il valore corretto per il default
                    db.execSQL("ALTER TABLE ordini ADD COLUMN OrderStrategy INTEGER DEFAULT ${OrderStrategyConstants.NORMAL}")
                }
                if (!columnExists(db, "ordini", "BatchMasterOrderID")) {
                    db.execSQL("ALTER TABLE ordini ADD COLUMN BatchMasterOrderID INTEGER DEFAULT NULL")
                }
                if (!columnExists(db, "ordini", "ristorante")) {
                    db.execSQL("ALTER TABLE ordini ADD COLUMN ristorante TEXT DEFAULT NULL")
                }
                Log.d("DatabaseHelper", "Upgrade per le nuove colonne di 'ordini' completato (o colonne già esistenti).")
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Errore durante l'aggiunta delle nuove colonne a ordini: ${e.message}", e)
            }
        }
    }

    private fun columnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        var cursor: android.database.Cursor? = null
        try {
            // Querying with LIMIT 0 è un modo efficiente per ottenere i metadati della colonna
            cursor = db.rawQuery("SELECT * FROM $tableName LIMIT 0", null)
            return cursor?.getColumnIndex(columnName) != -1
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error checking if column exists $tableName.$columnName", e)
            return false // In caso di errore, assumi che non esista per tentare l'ALTER
        } finally {
            cursor?.close()
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
                }
            }
            // Verifica e crea tabelle mancanti in ogni caso

            val db = writableDatabase
            ensureTablesExist(db)
            populateProvidersIfNeeded()
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
        Log.d("DatabaseHelper", "Tabella 'Providers' creata o già esistente.")
        populateProviders(db)

        val createMonthlySummariesTable = context.getString(R.string.Table_MontlySummaries)
        db.execSQL(createMonthlySummariesTable)
        Log.d("DatabaseHelper", "Tabella 'MonthlySummaries' creata o già esistente.")

        val createDailyOrdersTable = context.getString(R.string.Table_DailyOrders)
        db.execSQL(createDailyOrdersTable)
        Log.d("DatabaseHelper", "Tabella 'DailyOrders' creata o già esistente.")

        val createInviteCodeTable = context.getString(R.string.Table_InviteCode)
        db.execSQL(createInviteCodeTable)
        Log.d("DatabaseHelper", "Tabella 'InviteCode' creata o già esistente.")
    }

    private fun ensureTablesExist(db: SQLiteDatabase) {
        val tables = listOf(
            "ordini" to context.getString(R.string.Table_Ordini),
            "Providers" to context.getString(R.string.Table_Providders),
            "MonthlySummaries" to context.getString(R.string.Table_MontlySummaries),
            "DailyOrders" to context.getString(R.string.Table_DailyOrders),
            "InviteCode" to context.getString(R.string.Table_InviteCode),
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
                populateProviders(db)
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

    fun insertOrder(
        order: OrderData,
        relatedOrders: Map<Long, Int> = emptyMap()
    ): Long {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                // Valida ProviderID
                val cursor = db.rawQuery("SELECT ID FROM Providers WHERE ID = ?", arrayOf(order.providerID.toString()))
                if (!cursor.moveToFirst()) {
                    Log.e("DatabaseHelper", "Invalid ProviderID: ${order.providerID}")
                    cursor.close()
                    return -1L
                }
                cursor.close()
                // Determina la strategia dell'ordine corrente
                var orderStrategy = if (relatedOrders.any { it.value == OrderStrategyConstants.SERIAL }) {
                    order.orderStrategy // Mantieni SERIAL o SERIAL_PARALLEL
                } else {
                    order.orderStrategy and OrderStrategyConstants.PARALLEL // Azzera il bit seriale
                }
                // Se ci sono relazioni parallele, assicura che il bit parallelo sia settato
                if (relatedOrders.any { it.value == OrderStrategyConstants.PARALLEL }) {
                    orderStrategy = orderStrategy or OrderStrategyConstants.PARALLEL
                }
                Log.d("DatabaseHelper", "Order strategy for insertion: $orderStrategy, relatedOrders=$relatedOrders")

                // Inserisci ordine corrente
                val values = ContentValues().apply {
                    put("Data", order.data)
                    put("ProviderID", order.providerID)
                    put("PagaBase", order.pagaBase)
                    val riscossiContantiNetti = if (order.providerID == 2) {
                        order.riscossiContanti - order.pagatoContantiRistorante
                    } else {
                        order.riscossiContanti
                    }
                    put("RiscossiContanti", riscossiContantiNetti)
                    put("PagaExtra", order.pagaExtra)
                    put("Mancia", order.mancia + order.manciaContanti)
                    put("ManciaContanti", order.manciaContanti)
                    put("NumeroOrdini", order.numeroOrdini)
                    put("TempoImpiegato", order.tempoImpiegato)
                    put("PagaTotale", order.pagaTotale)
                    put("PagaOraria", order.pagaOraria)
                    put("StartTime", order.startTime.toString())
                    put("EndTime", order.endTime.toString())
                    put("OrderStrategy", orderStrategy)
                    if (order.batchMasterOrderID != null) {
                        put("BatchMasterOrderID", order.batchMasterOrderID)
                    } else {
                        putNull("BatchMasterOrderID")
                    }
                    put("ristorante", order.ristorante)
                }

                Log.d("DatabaseHelper", "Inserting order with values: $values")
                val insertId = db.insertOrThrow("ordini", null, values)
                order.id = insertId

                // Aggiorna ordini paralleli basandosi su relatedOrders
                if (relatedOrders.isNotEmpty()) {
                    val parallelIds = relatedOrders
                        .filter { it.value == OrderStrategyConstants.PARALLEL }
                        .keys
                        .toList()

                    if (parallelIds.isNotEmpty()) {
                        val idsPlaceholder = parallelIds.joinToString(",") { "?" }
                        val query = "UPDATE ordini SET TempoImpiegato = ?, PagaOraria = ?, BatchMasterOrderID = ?, OrderStrategy = ? WHERE ID IN ($idsPlaceholder)"
                        val args = arrayOf(
                            order.tempoImpiegato.toString(),
                            order.pagaOraria.toString(),
                            order.batchMasterOrderID?.toString() ?: "NULL",
                            OrderStrategyConstants.PARALLEL.toString(),
                            *parallelIds.map { it.toString() }.toTypedArray()
                        )
                        db.execSQL(query, args)
                        Log.d("DatabaseHelper", "Updated parallel orders with IDs: $parallelIds, TempoImpiegato=${order.tempoImpiegato}, PagaOraria=${order.pagaOraria}, BatchMasterOrderID=${order.batchMasterOrderID}")
                    }

                    // Aggiorna il primo ordine con serialità inversa (per SERIAL o SERIAL_PARALLEL)
                    if (order.orderStrategy == OrderStrategyConstants.SERIAL ||
                        order.orderStrategy == OrderStrategyConstants.SERIAL_PARALLEL) {
                        val UpdateID = relatedOrders.filter { it.value == -1 }.keys.firstOrNull()
                        if (UpdateID != null) {
                            val endTimeString = order.endTime.toString()
                            val serialUpdateQuery = """
                            WITH DiffSeconds AS (
                                SELECT CASE 
                                    WHEN (strftime('%s', EndTime) - strftime('%s', ?)) < 0 
                                    THEN ((strftime('%s', EndTime) - strftime('%s', ?)) + 86400 ) / 60
                                    ELSE ((strftime('%s', EndTime) - strftime('%s', ?)) / 60)
                                END AS diff
                                FROM Ordini
                                WHERE ID = ?
                            )
                            UPDATE Ordini
                            SET 
                                TempoImpiegato = (SELECT diff FROM DiffSeconds),
                                PagaOraria = PagaTotale * 60 / (SELECT diff FROM DiffSeconds),
                                OrderStrategy = OrderStrategy | ?
                            WHERE ID = ?
                        """.trimIndent()
                            val args = arrayOf(
                                endTimeString,
                                endTimeString,
                                endTimeString,
                                UpdateID.toString(),
                                OrderStrategyConstants.SERIAL.toString(),
                                UpdateID.toString()
                            )
                            db.execSQL(serialUpdateQuery, args)
                            Log.d("DatabaseHelper", "Updated serial inverse order with ID: $UpdateID, endTimeString=$endTimeString")
                        }
                    }
                }

                db.setTransactionSuccessful()
                Log.d("DatabaseHelper", "Order inserted with ID: $insertId, OrderStrategy=${order.orderStrategy}, relatedOrders=$relatedOrders")
                return insertId
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Error inserting order: ${e.message}", e)
                return -1L
            } finally {
                db.endTransaction()
            }
        }
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
            SUM(CASE 
                WHEN BatchMasterOrderID IS NULL OR ID = BatchMasterOrderID 
                THEN TempoImpiegato 
                ELSE 0 
            END) as totalTempoImpiegato,
            CASE 
                WHEN SUM(CASE 
                    WHEN BatchMasterOrderID IS NULL OR ID = BatchMasterOrderID 
                    THEN TempoImpiegato 
                    ELSE 0 
                END) = 0 THEN 0.0
                ELSE (SUM(PagaTotale) / (SUM(CASE 
                    WHEN BatchMasterOrderID IS NULL OR ID = BatchMasterOrderID 
                    THEN TempoImpiegato 
                    ELSE 0 
                END) / 60.0)) 
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

    fun getOrdineById(id: Long): OrderData? {
        val db = readableDatabase
        val cursor = db.query(
            "ordini", null, "ID = ?", arrayOf(id.toString()), null, null, null
        )
        return if (cursor.moveToFirst()) {
            val ordine = OrderData(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("ID")),
                data = cursor.getString(cursor.getColumnIndexOrThrow("Data")),
                providerID = cursor.getInt(cursor.getColumnIndexOrThrow("ProviderID")),
                pagaBase = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaBase")),
                pagaExtra = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaExtra")),
                mancia = cursor.getDouble(cursor.getColumnIndexOrThrow("Mancia")),
                manciaContanti = cursor.getDouble(cursor.getColumnIndexOrThrow("ManciaContanti")),
                riscossiContanti = cursor.getDouble(cursor.getColumnIndexOrThrow("RiscossiContanti")),
                numeroOrdini = cursor.getInt(cursor.getColumnIndexOrThrow("NumeroOrdini")),
                tempoImpiegato = cursor.getInt(cursor.getColumnIndexOrThrow("TempoImpiegato")),
                pagaTotale = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaTotale")),
                pagaOraria = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaOraria")),
                startTime = java.sql.Time.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("StartTime"))),
                endTime =java.sql.Time.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("EndTime"))),
                orderStrategy = cursor.getInt(cursor.getColumnIndexOrThrow("OrderStrategy")),
                ristorante = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("ristorante"))?:"",
            )
            // Gestione BatchMasterOrderID nullable
            val batchMasterIdIndex = cursor.getColumnIndexOrThrow("BatchMasterOrderID")
            if (!cursor.isNull(batchMasterIdIndex)) {
                ordine.batchMasterOrderID = cursor.getLong(batchMasterIdIndex)
            } else {
                ordine.batchMasterOrderID = null
            }
            cursor.close()
            ordine
        } else {
            cursor.close()
            null
        }
    }

    fun deleteOrdine(id: Long, deleteRecord: Boolean = true) {
        Log.d("DatabaseHelper", "deleteOrdine called with id: $id")
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Verifica l'esistenza dell'ordine e se ha relatedOrders
            val orderQuery = """
            SELECT Data, StartTime, EndTime, BatchMasterOrderID, OrderStrategy
            FROM Ordini
            WHERE ID = ?
        """.trimIndent()
            val orderCursor = db.rawQuery(orderQuery, arrayOf(id.toString()))
            if (!orderCursor.moveToFirst()) {
                Log.e("DatabaseHelper", "Ordine non trovato: ID=$id")
                orderCursor.close()
                db.setTransactionSuccessful()
                return
            }
            val data = orderCursor.getString(0)
            val startTime = orderCursor.getString(1)
            val endTime = orderCursor.getString(2)
            val batchMasterOrderId = orderCursor.getLongOrNull(3)
            val orderStrategy = orderCursor.getInt(4)
            orderCursor.close()

            val candidateOrders = getCandidateMultiAppOrders(data, startTime.substring(0, 5), endTime.substring(0, 5),id)
            Log.d("DatabaseHelper", "Candidate orders: $candidateOrders")
            if (candidateOrders.isEmpty()) {
                // Ordine isolato si può procedere con l'eliminazione.
                if (deleteRecord) db.delete("ordini", "ID = ?", arrayOf(id.toString()))
            } else {
                // Dobbiamo aggiornare gli ordini paralleli e seriali
                val relatedOrders = getRelatedOrdersForOrder(id, startTime.substring(0, 5), endTime.substring(0, 5), orderStrategy, batchMasterOrderId, candidateOrders)

                // Group related orders by batch
                val batches = mutableMapOf<Long?, MutableList<CandidateOrder>>()
                relatedOrders.keys.forEach { relId ->
                    val relOrder = candidateOrders.find { it.ID == relId }
                    if (relOrder != null) {
                        val master = relOrder.BatchMasterOrderID ?: relOrder.ID
                        batches.getOrPut(master) { mutableListOf() }.add(relOrder)
                    }
                }

                batches.forEach { (master, batchList) ->
                    // Filter out the deleted order
                    val remaining = batchList.filter { it.ID != id }
                    if (remaining.size < 2) {
                        // Reset remaining to NORMAL and recalculate individually
                        remaining.forEach { relOrder ->
                            updateOrderStrategy(db, relOrder.ID, OrderStrategyConstants.NORMAL, null)
                            recalculateSingleOrder(db, relOrder.ID)
                        }
                    } else {
                        // Keep batch, but if deleted was master, choose new master
                        val newMaster = if (master == id) {
                            remaining.firstOrNull()?.ID // Choose new master
                        } else {
                            master // Keep existing master
                        }
                        if (newMaster != null) {
                            // Update all to new master
                            remaining.forEach { relOrder ->
                                updateOrderStrategy(db, relOrder.ID, relOrder.OrderStrategy, newMaster)
                            }
                            // Recalculate batch totals
                            recalculateBatch(db, newMaster)
                        } else {
                            // No valid master, reset to NORMAL
                            remaining.forEach { relOrder ->
                                updateOrderStrategy(db, relOrder.ID, OrderStrategyConstants.NORMAL, null)
                                recalculateSingleOrder(db, relOrder.ID)
                            }
                        }
                    }
                }

                // Handle serial dependencies separately
                relatedOrders.forEach { (relId, relStrategy) ->
                    if (relStrategy and OrderStrategyConstants.SERIAL != 0 && relId != id) {
                        // For serial orders, reset and recalculate individual if no longer part of batch
                        if (!isPartOfBatch(db, relId)) {
                            updateOrderStrategy(db, relId, OrderStrategyConstants.NORMAL, null)
                        }
                        recalculateSingleOrder(db, relId) // Adjust time if overlap was subtracted
                    }
                }

                // Finally delete the order
                if (deleteRecord) db.delete("ordini", "ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Errore durante eliminazione ordine multiapp: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
    }

    private fun getRelatedOrdersForOrder(
        id: Long,
        startTime: String,
        endTime: String,
        orderStrategy: Int,
        batchMasterOrderID: Long?,
        candidateOrders: List<CandidateOrder>
    ): Map<Long, Int> {
        val related = mutableMapOf<Long, Int>()
        candidateOrders.forEach { candidate ->
            // Related if same batch
            if (candidate.BatchMasterOrderID == id || candidate.ID == batchMasterOrderID || candidate.BatchMasterOrderID == batchMasterOrderID) {
                related[candidate.ID] = candidate.OrderStrategy
            }
            // Or if serial dependency (overlap and strategy includes SERIAL)
            else if ((orderStrategy and OrderStrategyConstants.SERIAL != 0) || (candidate.OrderStrategy and OrderStrategyConstants.SERIAL != 0)) {
                // Check if start of one is during the other
                val isSerialOverlap = isSerialDependency(startTime, endTime, candidate.StartTime.toString().substring(0, 5), candidate.EndTime.toString().substring(0, 5))
                if (isSerialOverlap) {
                    related[candidate.ID] = OrderStrategyConstants.SERIAL
                }
            }
        }
        return related
    }

    private fun isSerialDependency(start1: String, end1: String, start2: String, end2: String): Boolean {
        val s1 = LocalTime.parse(start1, DateTimeFormatter.ofPattern("HH:mm"))
        val e1 = LocalTime.parse(end1, DateTimeFormatter.ofPattern("HH:mm"))
        val s2 = LocalTime.parse(start2, DateTimeFormatter.ofPattern("HH:mm"))
        val e2 = LocalTime.parse(end2, DateTimeFormatter.ofPattern("HH:mm"))
        // Serial if one starts during or after the other ends, but with overlap (for dependency)
        return (s2.isAfter(s1) && s2.isBefore(e1)) || (s1.isAfter(s2) && s1.isBefore(e2))
    }

    private fun updateOrderStrategy(db: SQLiteDatabase, orderId: Long, newStrategy: Int, newBatchMaster: Long?) {
        val values = ContentValues().apply {
            put("OrderStrategy", newStrategy)
            put("BatchMasterOrderID", newBatchMaster)
        }
        db.update("ordini", values, "ID = ?", arrayOf(orderId.toString()))
    }

    private fun recalculateSingleOrder(db: SQLiteDatabase, orderId: Long) {
        val query = "SELECT StartTime, EndTime, PagaTotale FROM Ordini WHERE ID = ?"
        val cursor = db.rawQuery(query, arrayOf(orderId.toString()))
        if (cursor.moveToFirst()) {
            val start = cursor.getString(0)
            val end = cursor.getString(1)
            val pagaTotale = cursor.getDouble(2)

            val tempoMinuti = TimeUtils.timeToMinutes(end) - TimeUtils.timeToMinutes(start)
            val tempoImpiegato = if (tempoMinuti > 0) tempoMinuti else tempoMinuti + 1440 // Handle overnight
            val pagaOraria = if (tempoImpiegato > 0) (pagaTotale * 60) / tempoImpiegato else 0.0

            val values = ContentValues().apply {
                put("TempoImpiegato", tempoImpiegato)
                put("PagaOraria", pagaOraria)
            }
            db.update("ordini", values, "ID = ?", arrayOf(orderId.toString()))
        }
        cursor.close()
    }

    private fun recalculateBatch(db: SQLiteDatabase, batchMasterId: Long) {
        val query = "SELECT ID, StartTime, EndTime, PagaTotale FROM Ordini WHERE BatchMasterOrderID = ? OR ID = ?"
        val cursor = db.rawQuery(query, arrayOf(batchMasterId.toString(), batchMasterId.toString()))
        val batchOrders = mutableListOf<OrderData>() // Assume populate list from cursor
        while (cursor.moveToNext()) {
            // Populate batchOrders with necessary fields
            val ord = OrderData(
                id = cursor.getLong(0),
                startTime = Time.valueOf(cursor.getString(1)),
                endTime = Time.valueOf(cursor.getString(2)),
                pagaTotale = cursor.getDouble(3),
                // Fill other fields as needed
            )
            batchOrders.add(ord)
        }
        cursor.close()

        if (batchOrders.isNotEmpty()) {
            val totalPay = batchOrders.sumOf { it.pagaTotale }
            val batchStart = batchOrders.minOf { it.startTime.toString() }
            val batchEnd = batchOrders.maxOf { it.endTime.toString() }
            val totalTimeMin = TimeUtils.timeToMinutes(batchEnd) - TimeUtils.timeToMinutes(batchStart)
            val pagaOraria = if (totalTimeMin > 0) (totalPay * 60) / totalTimeMin else 0.0

            batchOrders.forEach { ord ->
                val values = ContentValues().apply {
                    put("TempoImpiegato", totalTimeMin)
                    put("PagaOraria", pagaOraria)
                }
                db.update("ordini", values, "ID = ?", arrayOf(ord.id.toString()))
            }
        }
    }

    private fun isPartOfBatch(db: SQLiteDatabase, orderId: Long): Boolean {
        val query = "SELECT BatchMasterOrderID FROM Ordini WHERE ID = ?"
        val cursor = db.rawQuery(query, arrayOf(orderId.toString()))
        val hasBatch = if (cursor.moveToFirst()) cursor.getLongOrNull(0) != null else false
        cursor.close()
        return hasBatch
    }

    fun saveOrdine(ordine: OrderData): Long {
        Log.d("DatabaseHelper", "saveOrdine called with ordine: $ordine")
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Data", ordine.data)
            put("ProviderID", ordine.providerID)
            put("PagaBase", ordine.pagaBase)
            put("PagaExtra", ordine.pagaExtra)
            put("Mancia", ordine.mancia)
            put("ManciaContanti", ordine.manciaContanti)
            put("RiscossiContanti", ordine.riscossiContanti)
            put("NumeroOrdini", ordine.numeroOrdini)
            put("TempoImpiegato", ordine.tempoImpiegato)
            put("PagaTotale", ordine.pagaTotale)
            put("PagaOraria", ordine.pagaOraria)
            put("StartTime", ordine.startTime.toString())
            put("EndTime", ordine.endTime.toString())
            put("OrderStrategy", ordine.orderStrategy)
            if (ordine.batchMasterOrderID != null)
                put("BatchMasterOrderID", ordine.batchMasterOrderID)
            else
                putNull("BatchMasterOrderID")
        }
        Log.d("DatabaseHelper", "Inserting ordine with values: $values")

        val newId = db.insert("ordini", null, values)
        if (newId == -1L) {
            Log.e("DatabaseHelper", "Failed to insert order")
        } else {
            Log.d("DatabaseHelper", "Inserted new order with ID=$newId")
        }
        return newId
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

    fun getOrders(sqlClause: String): List<Order> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT o.ID, o.ProviderID, p.Name AS ProviderName\n" +
                    "FROM ordini o\n" +
                    "JOIN Providers p ON o.ProviderID = p.ID\n" +
                    "$sqlClause\n" +
                    "ORDER BY o.ProviderID ASC, o.StartTime ASC, o.ID ASC",
            null
        )
        val orders = mutableListOf<Order>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow("ID"))
            val providerId = cursor.getInt(cursor.getColumnIndexOrThrow("ProviderID"))
            val providerName = cursor.getString(cursor.getColumnIndexOrThrow("ProviderName"))
            orders.add(Order(id, providerId, providerName))
        }
        cursor.close()
        Log.d("DatabaseHelper", "Ordini trovati (test diretto): $orders")
        return orders
    }

    fun getProviderSummaries(sqlClause: String): List<OrderListItem.Header> {
        val db = readableDatabase
        val query = """
            SELECT
            o.ProviderID,
            p.Name AS ProviderName,
            SUM(o.NumeroOrdini) AS TotalOrders,
            SUM(o.PagaTotale) AS TotalEarnings,
            SUM(o.Mancia) AS TotalTips,
            SUM(CASE
                    WHEN o.BatchMasterOrderID IS NULL THEN o.TempoImpiegato
                    ELSE (
                    SELECT TempoImpiegato
                            FROM ordini o2
                            WHERE o2.ID = o.BatchMasterOrderID
                    )
                    END) AS TotalTime,
            CASE
            WHEN SUM(CASE
                    WHEN o.BatchMasterOrderID IS NULL THEN o.TempoImpiegato
                    ELSE (
                    SELECT TempoImpiegato
                            FROM ordini o2
                            WHERE o2.ID = o.BatchMasterOrderID
                    )
                    END) > 0
            THEN SUM(CASE
                    WHEN o.BatchMasterOrderID IS NULL THEN o.PagaTotale
                    ELSE (
                    SELECT SUM(PagaTotale)
                            FROM ordini o2
                            WHERE o2.BatchMasterOrderID = o.BatchMasterOrderID
                            OR o2.ID = o.BatchMasterOrderID
                    )
                    END) * 60.0 / SUM(CASE
                    WHEN o.BatchMasterOrderID IS NULL THEN o.TempoImpiegato
                    ELSE (
                    SELECT TempoImpiegato
                            FROM ordini o2
                            WHERE o2.ID = o.BatchMasterOrderID
                    )
                    END)
            ELSE 0.0
            END AS HourlyRate,
            SUM(o.RiscossiContanti + o.ManciaContanti) AS TotalContanti,
            SUM(o.ManciaContanti) AS TotalManciaContanti
            FROM ordini o
            JOIN Providers p ON o.ProviderID = p.ID
            $sqlClause
            GROUP BY o.ProviderID, p.Name
            ORDER BY o.ProviderID ASC
            """.trimIndent()

        val cursor = db.rawQuery(
            query,
            null
        )

        val summaries = mutableListOf<OrderListItem.Header>()
        while (cursor.moveToNext()) {
            val providerId = cursor.getInt(cursor.getColumnIndexOrThrow("ProviderID"))
            val providerName = cursor.getString(cursor.getColumnIndexOrThrow("ProviderName"))
            val totalOrders = cursor.getInt(cursor.getColumnIndexOrThrow("TotalOrders"))
            val totalEarnings = cursor.getDouble(cursor.getColumnIndexOrThrow("TotalEarnings"))
            val totalTips = cursor.getDouble(cursor.getColumnIndexOrThrow("TotalTips"))
            val totalTime = cursor.getInt(cursor.getColumnIndexOrThrow("TotalTime"))
            val hourlyRate = if (totalTime > 0) {
                cursor.getDouble(cursor.getColumnIndexOrThrow("HourlyRate"))
            } else 0.0
            val totalContanti = cursor.getDouble(cursor.getColumnIndexOrThrow("TotalContanti"))
            val totalManciaContanti = cursor.getDouble(cursor.getColumnIndexOrThrow("TotalManciaContanti"))

            summaries.add(
                OrderListItem.Header(
                    providerName = providerName,
                    providerId = providerId,
                    totalOrders = totalOrders,
                    totalEarnings = totalEarnings,
                    hourlyRate = hourlyRate,
                    totalTips = totalTips,
                    totalContanti = totalContanti,
                    totalManciaContanti = totalManciaContanti,
                    totalTempo = totalTime
                )
            )
        }
        cursor.close()
        Log.d("DatabaseHelper", "Provider summaries: $summaries")
        return summaries
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
        GROUP BY ProviderID
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

    private fun populateProvidersIfNeeded() {
        val db = writableDatabase

        // Get expected providers from Populate_Providers for validation
        val populateProvidersSql = context.getString(R.string.Populate_Providers)
        val expectedProviders = mutableMapOf<Int, String>()
        val providerStatements = populateProvidersSql.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        providerStatements.forEach { statement ->
            try {
                // Extract ID and Name from INSERT statement
                val regex = """INSERT OR IGNORE INTO Providers \(ID, Name\) VALUES \((\d+), '([^']+)'\)""".toRegex()
                val match = regex.find(statement)
                if (match != null) {
                    val id = match.groupValues[1].toInt()
                    val name = match.groupValues[2]
                    expectedProviders[id] = name
                } else {
                    Log.e("DatabaseHelper", "Invalid provider statement: $statement")
                }
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Error parsing provider statement '$statement': ${e.message}", e)
            }
        }

        // Log expected providers
        Log.d("DatabaseHelper", "Expected providers (${expectedProviders.size}): $expectedProviders")
        if (expectedProviders.size != 22) {
            Log.e("DatabaseHelper", "Expected 22 providers, found ${expectedProviders.size} in Populate_Providers")
        }

        // Check existing providers
        val cursor = db.rawQuery("SELECT ID, Name FROM Providers", null)
        val existingProviders = mutableMapOf<Int, String>()
        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("Name"))
            existingProviders[id] = name
            Log.d("DatabaseHelper", "Found provider: ID=$id, Name=$name")
        }
        cursor.close()

        // Log existing providers
        Log.d("DatabaseHelper", "Existing providers (${existingProviders.size}): $existingProviders")

        // Insert missing providers
        expectedProviders.forEach { (id, name) ->
            if (!existingProviders.containsKey(id)) {
                Log.d("DatabaseHelper", "Missing provider: ID=$id, Name=$name")
                try {
                    db.execSQL(
                        "INSERT OR IGNORE INTO Providers (ID, Name) VALUES (?, ?)",
                        arrayOf(id, name)
                    )
                    Log.d("DatabaseHelper", "Inserted provider: ID=$id, Name=$name")
                } catch (e: Exception) {
                    Log.e("DatabaseHelper", "Error inserting provider ID=$id, Name=$name: ${e.message}", e)
                }
            } else if (existingProviders[id] != name) {
                Log.w("DatabaseHelper", "Provider ID=$id has name '${existingProviders[id]}', expected '$name'. Updating.")
                try {
                    db.execSQL(
                        "UPDATE Providers SET Name = ? WHERE ID = ?",
                        arrayOf(name, id)
                    )
                    Log.d("DatabaseHelper", "Updated provider: ID=$id, Name=$name")
                } catch (e: Exception) {
                    Log.e("DatabaseHelper", "Error updating provider ID=$id: ${e.message}", e)
                }
            }
        }

        // Verify final state
        val finalCursor = db.rawQuery("SELECT ID, Name FROM Providers", null)
        val finalProviders = mutableListOf<String>()
        while (finalCursor.moveToNext()) {
            val id = finalCursor.getInt(finalCursor.getColumnIndexOrThrow("ID"))
            val name = finalCursor.getString(finalCursor.getColumnIndexOrThrow("Name"))
            finalProviders.add("ID=$id, Name=$name")
        }
        finalCursor.close()
        Log.d("DatabaseHelper", "Final providers (${finalProviders.size}): ${finalProviders.joinToString()}")

        if (finalProviders.size != 22) {
            Log.e("DatabaseHelper", "Provider population incomplete: expected 22, found ${finalProviders.size}")
        } else {
            Log.d("DatabaseHelper", "All 22 providers populated successfully")
        }
    }

    private fun populateProviders(db: SQLiteDatabase) {
        val populateProvidersSql = context.getString(R.string.Populate_Providers)
        val providerStatements = populateProvidersSql.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        providerStatements.forEach { sql ->
            try {
                db.execSQL(sql)
                Log.d("DatabaseHelper", "Executed: $sql")
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Error executing '$sql': ${e.message}", e)
            }
        }
        // Log total inserted
        val cursor = db.rawQuery("SELECT COUNT(*) FROM Providers", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        Log.d("DatabaseHelper", "Providers inserted, total count: $count")
    }

    fun getProviders(): List<Pair<Int, String>> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT ID, Name FROM Providers ORDER BY ID ASC", null)
        val providers = mutableListOf<Pair<Int, String>>()

        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"))
            val name = cursor.getString(cursor.getColumnIndexOrThrow("Name"))
            providers.add(id to name)
        }

        cursor.close()
        return providers
    }

    fun getCandidateMultiAppOrders(data: String, startTime: String, endTime: String, excludeID: Long? = null): List<CandidateOrder> {
        val orders = mutableListOf<CandidateOrder>()
        val db = readableDatabase
        var params = mutableListOf<String>(data, "$endTime:00", "$startTime:00")
        try {
            var query = """
            SELECT Ordini.ID, Providers.name AS ProviderName, Ordini.ristorante, 
                   Ordini.StartTime, Ordini.EndTime, Ordini.PagaTotale, ordini.OrderStrategy, ordini.BatchMasterOrderID, ordini.TempoImpiegato
            FROM Ordini 
            INNER JOIN providers ON Ordini.ProviderID = Providers.ID
            WHERE Ordini.data = ? 
            AND Ordini.StartTime <= ?
            AND Ordini.EndTime >= ?
            """.trimIndent()
            if (excludeID != null && excludeID != -1L) {
                query += " AND Ordini.ID != ?"
                params.add(excludeID.toString())
            }
            query+= " ORDER BY Ordini.StartTime, Ordini.EndTime ASC"
            val cursor = db.rawQuery(query, params.toTypedArray())
            Log.d("DatabaseHelper", "Query: $query\nwith params $params \nreturned ${cursor.count} rows")
            while (cursor.moveToNext()) {
                val order = CandidateOrder(
                    ID = cursor.getLong(cursor.getColumnIndexOrThrow("ID")),
                    ProviderName = cursor.getString(cursor.getColumnIndexOrThrow("ProviderName")) ?: "",
                    Ristorante = cursor.getString(cursor.getColumnIndexOrThrow("ristorante")) ?: "",
                    StartTime = Time.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("StartTime"))),
                    EndTime = Time.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("EndTime"))),
                    PagaTotale = cursor.getDouble(cursor.getColumnIndexOrThrow("PagaTotale")),
                    OrderStrategy = cursor.getInt(cursor.getColumnIndexOrThrow("OrderStrategy")),
                    BatchMasterOrderID = cursor.getLong(cursor.getColumnIndexOrThrow("BatchMasterOrderID")),
                    TempoImpiegato = cursor.getInt(cursor.getColumnIndexOrThrow("TempoImpiegato"))
                )
                orders.add(order)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Errore nel recupero degli ordini candidati: ${e.message}")
        } finally {
            Log.d("DatabaseHelper", "Ordini candidati trovati: $orders")
        }
        return orders
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