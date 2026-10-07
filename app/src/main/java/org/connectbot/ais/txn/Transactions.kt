/*
 * AIS Terminal — additions to ConnectBot
 * Developed by DT (AIS Glass, Supply Chain Planning)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ais.txn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.ais.AisPrefs
import org.connectbot.ais.scanner.ScanTemplates
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * One operator menu (FGWH Receiving, Move to PDI, …). Admin-configurable:
 *  - [startSequence] keys sent once when the menu is opened (navigate to the EBS screen)
 *  - [scanTemplate]  keys sent for each scan; may use {QR.FIELD}, {M.COLUMN}, {S1}, {SCAN},
 *                    {DATE}, macro keys, and defaults like {M.SUBINV|FGWH}
 *  - [exitSequence]  keys sent when the operator leaves the menu
 */
data class TransactionConfig(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val hostNickname: String = "",
    val masterTable: String = "",
    // Default layout = AIS FG label QR, e.g.
    // @SF.T56.LFH.GAG21X0000@338914@53@CL1245.P09266458905@OEM CHN@27-SEP-2026@CL1245
    // The leading @ gives an empty first field, named "_" (ignored).
    val qrDelimiter: String = "AT",
    val qrFields: String = "_,ITEM,SERIAL,QTY,LOT,SUBINV,MFG_DATE,BATCH",
    val keyField: String = "ITEM",
    val startSequence: String = "",
    val scanTemplate: String = "",
    val exitSequence: String = "",
    val autoSend: Boolean = true,
    val requireMaster: Boolean = true,
) {
    fun fieldNames(): List<String> = qrFields.split(',').map { it.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }
}

object TransactionDefaults {
    val list = listOf(
        TransactionConfig(
            id = "FGWH_RECEIVING",
            title = "FGWH Receiving",
            scanTemplate = "{QR.ITEM}{TAB}{QR.LOT}{TAB}{QR.QTY|1}{TAB}{QR.SUBINV|FGWH}{TAB}{M.LOCATOR}{ENTER}",
        ),
        TransactionConfig(
            id = "MOVE_TO_PDI",
            title = "Move to PDI",
            scanTemplate = "{QR.ITEM}{TAB}{QR.LOT}{TAB}{QR.QTY|1}{TAB}{QR.SUBINV|FGWH}{TAB}PDI{ENTER}",
        ),
        TransactionConfig(
            id = "MOVE_TO_PACKING",
            title = "Move to Packing",
            scanTemplate = "{QR.ITEM}{TAB}{QR.LOT}{TAB}{QR.QTY|1}{TAB}PDI{TAB}PACKING{ENTER}",
        ),
        TransactionConfig(
            id = "LOTOUT",
            title = "Lotout",
            scanTemplate = "{QR.ITEM}{TAB}{QR.LOT}{TAB}{QR.QTY|1}{ENTER}",
        ),
        TransactionConfig(
            id = "ORG_TRANSFER",
            title = "Org Transfer",
            scanTemplate = "{QR.ITEM}{TAB}{QR.LOT}{TAB}{QR.QTY|1}{TAB}{M.TO_ORG}{ENTER}",
        ),
    )
}

class TransactionStore(context: Context) {
    private val prefs = AisPrefs(context)

    fun load(): List<TransactionConfig> {
        val saved = try {
            val arr = JSONArray(prefs.transactionsJson.ifBlank { "[]" })
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                o.getString("id") to fromJson(o)
            }
        } catch (e: Exception) {
            emptyMap()
        }
        // Always return the five standard menus, in order, with any saved configuration.
        return TransactionDefaults.list.map { saved[it.id] ?: it }
    }

    fun save(list: List<TransactionConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.transactionsJson = arr.toString()
    }

    fun update(config: TransactionConfig) = save(load().map { if (it.id == config.id) config else it })

    fun get(id: String): TransactionConfig? = load().firstOrNull { it.id == id }

    fun exportJson(): String {
        val arr = JSONArray()
        load().forEach { arr.put(toJson(it)) }
        return arr.toString(2)
    }

    fun importJson(json: String) {
        val arr = JSONArray(json)
        val incoming = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.associateBy { it.id }
        save(load().map { incoming[it.id] ?: it })
    }

    private fun toJson(c: TransactionConfig) = JSONObject()
        .put("id", c.id).put("title", c.title).put("enabled", c.enabled)
        .put("hostNickname", c.hostNickname).put("masterTable", c.masterTable)
        .put("qrDelimiter", c.qrDelimiter).put("qrFields", c.qrFields).put("keyField", c.keyField)
        .put("startSequence", c.startSequence).put("scanTemplate", c.scanTemplate)
        .put("exitSequence", c.exitSequence).put("autoSend", c.autoSend).put("requireMaster", c.requireMaster)

    private fun fromJson(o: JSONObject): TransactionConfig {
        val d = TransactionDefaults.list.firstOrNull { it.id == o.optString("id") } ?: TransactionDefaults.list.first()
        return TransactionConfig(
            id = o.optString("id", d.id),
            title = o.optString("title", d.title),
            enabled = o.optBoolean("enabled", true),
            hostNickname = o.optString("hostNickname"),
            masterTable = o.optString("masterTable"),
            qrDelimiter = o.optString("qrDelimiter", d.qrDelimiter),
            qrFields = o.optString("qrFields", d.qrFields),
            keyField = o.optString("keyField", d.keyField),
            startSequence = o.optString("startSequence"),
            scanTemplate = o.optString("scanTemplate", d.scanTemplate),
            exitSequence = o.optString("exitSequence"),
            autoSend = o.optBoolean("autoSend", true),
            requireMaster = o.optBoolean("requireMaster", true),
        )
    }
}

enum class ScanStatus { READY, SENT, PENDING_CONFIRM, NOT_IN_MASTER, ERROR }

data class ScanResult(
    val raw: String,
    /** Values shown to the operator: QR fields then master columns. */
    val values: List<Pair<String, String>>,
    val sequence: String,
    val status: ScanStatus,
    val message: String,
)

object TransactionEngine {
    /** Splits the QR, looks up the master row and builds the key sequence for this scan. */
    fun process(context: Context, config: TransactionConfig, raw: String): ScanResult {
        val trimmed = ScanTemplates.trimScan(raw, config.qrDelimiter)
        val parts = ScanTemplates.split(trimmed, config.qrDelimiter, true)
        val names = config.fieldNames()
        val qr = LinkedHashMap<String, String>()
        names.forEachIndexed { i, n -> qr[n] = parts.getOrElse(i) { "" } }
        val key = qr[config.keyField.trim().uppercase(Locale.US)] ?: parts.firstOrNull().orEmpty()

        val master: Map<String, String>? = if (config.masterTable.isNotBlank() && key.isNotBlank()) {
            MasterStore(context).lookup(config.masterTable, key)
        } else {
            null
        }

        val values = qr.filterKeys { it != "_" }.map { (k, v) -> "QR.$k" to v } +
            (master?.map { (k, v) -> "M.$k" to v } ?: emptyList())

        if (config.masterTable.isNotBlank() && master == null && config.requireMaster) {
            return ScanResult(trimmed, values, "", ScanStatus.NOT_IN_MASTER, "“$key” not found in ${config.masterTable}")
        }

        // One pass over the template, so scanned/master values are never re-read as tokens.
        val sequence = ScanTemplates.render(config.scanTemplate, trimmed, config.qrDelimiter, true) { name ->
            when {
                name.startsWith("QR.") -> qr[name.removePrefix("QR.")]
                name.startsWith("M.") -> master?.get(name.removePrefix("M."))
                else -> null
            }
        }
        val status = if (config.autoSend) ScanStatus.SENT else ScanStatus.PENDING_CONFIRM
        return ScanResult(trimmed, values, sequence, status, if (master != null) "Master found" else "")
    }
}

/** The menu the operator is working in, shared between the home screen and the console. */
object TransactionSession {
    /** Set by the home screen; the console for [pendingHostId] activates it when it opens. */
    @Volatile var pendingId: String? = null

    @Volatile var pendingHostId: Long? = null

    /** Host the active menu belongs to; other sessions do not use it. */
    @Volatile var activeHostId: Long? = null
        private set

    private val mutableActive = MutableStateFlow<TransactionConfig?>(null)
    val active: StateFlow<TransactionConfig?> = mutableActive.asStateFlow()

    private val mutableLast = MutableStateFlow<ScanResult?>(null)
    val last: StateFlow<ScanResult?> = mutableLast.asStateFlow()

    private val mutableCount = MutableStateFlow(0)
    val count: StateFlow<Int> = mutableCount.asStateFlow()

    fun start(config: TransactionConfig, hostId: Long?) {
        activeHostId = hostId
        mutableActive.value = config
        mutableLast.value = null
        mutableCount.value = 0
    }

    fun record(result: ScanResult) {
        mutableLast.value = result
        if (result.status == ScanStatus.SENT) mutableCount.value = mutableCount.value + 1
    }

    fun markSent() {
        mutableLast.value = mutableLast.value?.copy(status = ScanStatus.SENT)
        mutableCount.value = mutableCount.value + 1
    }

    fun stop() {
        activeHostId = null
        mutableActive.value = null
        mutableLast.value = null
    }
}
