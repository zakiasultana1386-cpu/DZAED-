package com.example.web

import com.example.data.local.LedgerTransactionEntity
import com.example.data.model.BusinessSettings
import com.example.data.model.PartySummary
import com.example.data.model.TransactionType
import com.example.data.repository.LedgerRepository
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections

class LocalLedgerWebServer(
    private val repository: LedgerRepository,
    private val settingsProvider: () -> BusinessSettings,
    private val tokenValidator: (username: String, token: String) -> Boolean = { _, _ -> true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    @Volatile
    var activePort: Int = 0
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false && activePort > 0

    fun start(preferredPort: Int = 8765): Int {
        if (isRunning) return activePort
        val socket = try {
            ServerSocket(preferredPort)
        } catch (_: Exception) {
            try {
                ServerSocket(0)
            } catch (_: Exception) {
                return 0
            }
        }
        serverSocket = socket
        activePort = socket.localPort

        serverJob = scope.launch {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handleClient(client) }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
        return activePort
    }

    fun stop() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
        } catch (_: Exception) {
        } finally {
            serverJob = null
            serverSocket = null
            activePort = 0
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            val parts = requestLine.split(" ")
            val rawTarget = if (parts.size >= 2) parts[1] else "/"
            val path = rawTarget.substringBefore("?")
            val query = rawTarget.substringAfter("?", "")
            val settings = settingsProvider()

            val queryParams = query.split("&")
                .mapNotNull { param ->
                    val kv = param.split("=", limit = 2)
                    if (kv.size == 2) {
                        URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8")
                    } else null
                }
                .toMap()

            val usernameFromPath = if (path.startsWith("/u/")) {
                URLDecoder.decode(path.removePrefix("/u/").trim('/'), "UTF-8")
            } else null

            val requestedUsername = (usernameFromPath ?: queryParams["u"])?.trim().orEmpty()

            val html = if (path.startsWith("/share")) {
                val token = queryParams["token"].orEmpty()
                if (requestedUsername.isEmpty() || !tokenValidator(requestedUsername, token)) {
                    renderLookupHomeHtml(
                        settings = settings,
                        prefillUsername = requestedUsername,
                        notFoundError = "This temporary shareable link has expired or is invalid."
                    )
                } else {
                    val match = runBlocking { repository.findPartyLedgerByUsername(requestedUsername) }
                    if (match != null) {
                        renderPartyStatementHtml(
                            summary = match.first,
                            transactions = match.second,
                            settings = settings,
                            searchedUsername = requestedUsername,
                            isLivePortal = false
                        )
                    } else {
                        renderLookupHomeHtml(
                            settings = settings,
                            prefillUsername = requestedUsername,
                            notFoundError = "No party ledger found for username \"@$requestedUsername\"."
                        )
                    }
                }
            } else if (requestedUsername.isNotEmpty()) {
                val match = runBlocking { repository.findPartyLedgerByUsername(requestedUsername) }
                if (match != null) {
                    renderPartyStatementHtml(
                        summary = match.first,
                        transactions = match.second,
                        settings = settings,
                        searchedUsername = requestedUsername,
                        isLivePortal = true
                    )
                } else {
                    renderLookupHomeHtml(
                        settings = settings,
                        prefillUsername = requestedUsername,
                        notFoundError = "No party ledger found for username \"@$requestedUsername\". Please check spelling and try again."
                    )
                }
            } else {
                renderLookupHomeHtml(settings = settings)
            }

            writeHttpResponse(socket.getOutputStream(), html)
        }
    }

    private fun writeHttpResponse(out: OutputStream, bodyHtml: String) {
        val bytes = bodyHtml.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    companion object {
        fun getLocalIpv4Address(): String? {
            return try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }

        private fun esc(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun renderLookupHomeHtml(
            settings: BusinessSettings,
            prefillUsername: String = "",
            notFoundError: String? = null
        ): String {
            return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${esc(settings.businessName)} — Party Ledger Lookup</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background:#f4f7f6; color:#0f172a; margin:0; padding:24px 16px; }
  .card { max-width:520px; margin:32px auto; background:#fff; border-radius:20px; padding:28px; box-shadow:0 10px 30px rgba(15,76,58,0.08); border:1px solid #d9e2de; }
  .badge { display:inline-block; background:#d1fae5; color:#064e3b; font-size:12px; font-weight:700; padding:4px 10px; border-radius:999px; margin-bottom:10px; }
  h1 { margin:0 0 8px; font-size:22px; color:#0f4c3a; }
  p { margin:0 0 18px; color:#475569; font-size:14px; line-height:1.5; }
  form { display:flex; flex-direction:column; gap:12px; }
  input { padding:14px 16px; border:1.5px solid #cbd5e1; border-radius:12px; font-size:16px; outline:none; }
  input:focus { border-color:#0f4c3a; }
  button { padding:14px; border:none; border-radius:12px; background:#0f4c3a; color:#fff; font-size:15px; font-weight:700; cursor:pointer; }
  .err { background:#fef2f2; color:#dc2626; border:1px solid #fecaca; padding:12px; border-radius:10px; font-size:13px; margin-bottom:14px; }
</style>
</head>
<body>
  <div class="card">
    <span class="badge">DZAED Portal</span>
    <h1>${esc(settings.businessName)}</h1>
    <p>Paste your unique party username below to view your live account balance, product pieces (Pcs), and complete transaction history.</p>
    ${if (notFoundError != null) "<div class=\"err\">${esc(notFoundError)}</div>" else ""}
    <form action="/" method="GET">
      <input type="text" name="u" value="${esc(prefillUsername)}" placeholder="Paste username (e.g. rahman_textiles)" required autofocus />
      <button type="submit">Find My History</button>
    </form>
  </div>
</body>
</html>
            """.trimIndent()
        }

        fun renderPartyStatementHtml(
            summary: PartySummary,
            transactions: List<LedgerTransactionEntity>,
            settings: BusinessSettings,
            searchedUsername: String = summary.party.username,
            isLivePortal: Boolean = false
        ): String {
            val p = summary.party
            val net = summary.netBalance
            val netClass = when {
                net > 0.005 -> "green"
                net < -0.005 -> "red"
                else -> "muted"
            }
            val rowsHtml = if (transactions.isEmpty()) {
                "<tr><td colspan=\"6\" style=\"text-align:center;color:#64748b;padding:20px;\">No transactions recorded yet.</td></tr>"
            } else {
                transactions.joinToString("\n") { tx ->
                    val type = TransactionType.fromRaw(tx.transactionType)
                    val typeBadgeClass = when (type) {
                        TransactionType.MONEY_RECEIVED -> "tag-green"
                        TransactionType.MONEY_GIVEN -> "tag-red"
                        TransactionType.PRODUCT_GIVEN -> "tag-emerald"
                        TransactionType.PRODUCT_TAKEN -> "tag-amber"
                    }
                    val itemCell = buildString {
                        if (tx.quantityPcs > 0) append("${tx.quantityPcs} Pcs")
                        if (tx.itemName.isNotBlank()) {
                            if (isNotEmpty()) append(" &bull; ")
                            append(esc(tx.itemName))
                        }
                        if (tx.unitPrice > 0.0) {
                            append(" (@ ${esc(Formatters.formatMoney(tx.unitPrice, settings.currencySymbol))})")
                        }
                        if (isEmpty()) append("&mdash;")
                    }
                    """
                    <tr>
                      <td class="mono">${esc(Formatters.formatDateSlash(tx.transactionDateMillis))}</td>
                      <td class="mono">${esc(tx.receiptNo)}</td>
                      <td><span class="tag $typeBadgeClass">${esc(type.label)}</span></td>
                      <td>$itemCell</td>
                      <td class="mono bold">${esc(Formatters.formatExactMoney(tx.amount, settings.currencySymbol))}</td>
                      <td>${esc(tx.notes.ifBlank { "—" })}</td>
                    </tr>
                    """.trimIndent()
                }
            }

            val searchHeader = if (isLivePortal) {
                """
                <form action="/" method="GET" class="searchbar no-print">
                  <input type="text" name="u" value="${esc(searchedUsername)}" placeholder="Search another username..." />
                  <button type="submit">Lookup</button>
                </form>
                """.trimIndent()
            } else ""

            return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${esc(p.companyName)} (@${esc(p.username)}) — ${esc(settings.businessName)}</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background:#f4f7f6; color:#0f172a; margin:0; padding:18px 14px; }
  .wrap { max-width:920px; margin:0 auto; }
  .searchbar { display:flex; gap:8px; margin-bottom:14px; }
  .searchbar input { flex:1; padding:11px 14px; border-radius:10px; border:1px solid #cbd5e1; font-size:14px; }
  .searchbar button { padding:11px 18px; border-radius:10px; border:none; background:#0f4c3a; color:#fff; font-weight:700; cursor:pointer; }
  .hero { background:linear-gradient(135deg,#0b3b2d,#0f4c3a); color:#fff; border-radius:18px; padding:22px; margin-bottom:16px; display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:14px; }
  .hero h1 { margin:0 0 4px; font-size:22px; }
  .hero .sub { color:#a7f3d0; font-size:14px; }
  .username-pill { display:inline-block; background:rgba(255,255,255,0.16); padding:4px 10px; border-radius:8px; font-family:monospace; font-size:13px; margin-top:8px; }
  .print-btn { background:#34d399; color:#032117; border:none; border-radius:10px; padding:10px 16px; font-weight:700; cursor:pointer; }
  .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; margin-bottom:16px; }
  .kpi { background:#fff; border-radius:14px; padding:14px 16px; border:1px solid #d9e2de; }
  .kpi .lbl { font-size:12px; color:#64748b; margin-bottom:4px; }
  .kpi .val { font-size:20px; font-weight:800; font-family:monospace; }
  .green { color:#059669; }
  .red { color:#dc2626; }
  .muted { color:#475569; }
  .table-card { background:#fff; border-radius:16px; border:1px solid #d9e2de; overflow-x:auto; }
  table { width:100%; border-collapse:collapse; font-size:13px; }
  th { text-align:left; background:#f8faf9; color:#475569; padding:12px 14px; border-bottom:1px solid #e2e8f0; font-size:12px; }
  td { padding:12px 14px; border-bottom:1px solid #f1f5f9; vertical-align:top; }
  .mono { font-family:monospace; }
  .bold { font-weight:700; }
  .tag { display:inline-block; padding:3px 8px; border-radius:6px; font-size:11px; font-weight:700; white-space:nowrap; }
  .tag-green { background:#ecfdf5; color:#059669; }
  .tag-red { background:#fef2f2; color:#dc2626; }
  .tag-emerald { background:#d1fae5; color:#064e3b; }
  .tag-amber { background:#fffbeb; color:#d97706; }
  @media print { .no-print { display:none !important; } body { background:#fff; } }
</style>
</head>
<body>
<div class="wrap">
  $searchHeader
  <div class="hero">
    <div>
      <div class="sub">Ledger Statement with <strong>${esc(settings.businessName)}</strong></div>
      <h1>${esc(p.companyName)}</h1>
      <div class="sub">${esc(p.personName)} &bull; ${esc(p.phoneNumber)}${if (p.address.isNotBlank()) " &bull; ${esc(p.address)}" else ""}</div>
      <div class="username-pill">@${esc(p.username)}</div>
    </div>
    <button class="print-btn no-print" onclick="window.print()">Print / Save PDF</button>
  </div>

  <div class="grid">
    <div class="kpi">
      <div class="lbl">Total Receivable (Owed to Business)</div>
      <div class="val green">${esc(Formatters.formatMoney(summary.totalReceivable, settings.currencySymbol))}</div>
    </div>
    <div class="kpi">
      <div class="lbl">Total Payable (Business Owes)</div>
      <div class="val red">${esc(Formatters.formatMoney(summary.totalPayable, settings.currencySymbol))}</div>
    </div>
    <div class="kpi">
      <div class="lbl">Net Balance</div>
      <div class="val $netClass">${esc(Formatters.formatSignedBalance(net, settings.currencySymbol))}</div>
    </div>
    <div class="kpi">
      <div class="lbl">Product Pieces (Pcs)</div>
      <div class="val">${summary.totalPcsGiven} Given / ${summary.totalPcsTaken} Taken</div>
    </div>
  </div>

  <div class="table-card">
    <table>
      <thead>
        <tr>
          <th>Date</th>
          <th>Receipt No</th>
          <th>Type</th>
          <th>Product / Pcs</th>
          <th>Amount</th>
          <th>Notes</th>
        </tr>
      </thead>
      <tbody>
        $rowsHtml
      </tbody>
    </table>
  </div>
</div>
</body>
</html>
            """.trimIndent()
        }
    }
}
