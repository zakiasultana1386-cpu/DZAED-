package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.local.LedgerTransactionEntity
import com.example.data.model.BusinessSettings
import com.example.data.model.MonthlyCashFlowSummary
import com.example.data.model.PartySummary
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithParty
import com.example.web.LocalLedgerWebServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ReceiptExportHelper {

    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun buildReceiptRows(
        item: TransactionWithParty,
        settings: BusinessSettings
    ): List<Pair<String, String>> {
        val tx = item.transaction
        val party = item.party
        val type = item.type
        val partyLabel = if (type.isInflowToBusiness) "Received From" else "Paid / Delivered To"
        val handlerLabel = if (type.isInflowToBusiness) "Received By" else "Paid / Delivered By"
        val partyValue = "${party.companyName} — ${party.personName}"

        val qtyText = when {
            tx.quantityPcs > 0 && tx.itemName.isNotBlank() -> {
                if (tx.unitPrice > 0.0) {
                    "${tx.quantityPcs} Pcs • ${tx.itemName} (@ ${Formatters.formatMoney(tx.unitPrice, settings.currencySymbol)}/pc)"
                } else {
                    "${tx.quantityPcs} Pcs • ${tx.itemName}"
                }
            }
            tx.quantityPcs > 0 -> "${tx.quantityPcs} Pcs"
            tx.itemName.isNotBlank() -> tx.itemName
            else -> "Cash Transaction (${type.label})"
        }

        val amountText = Formatters.formatExactMoney(tx.amount, settings.currencySymbol)
        val paymentForText = tx.notes.ifBlank { type.label }
        val addressText = party.address.ifBlank { "—" }
        val phoneText = party.phoneNumber.ifBlank { "—" }
        val balanceText = Formatters.formatSignedBalance(
            item.partyCurrentNetBalance,
            settings.currencySymbol
        )

        return listOf(
            partyLabel to partyValue,
            "Quantity / Items" to qtyText,
            "Amount" to amountText,
            "Payment For" to paymentForText,
            handlerLabel to settings.businessName,
            "Address" to addressText,
            "Phone Number" to phoneText,
            "Remaining Due / Balance" to balanceText
        )
    }

    private fun drawReceiptOnCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        item: TransactionWithParty,
        settings: BusinessSettings,
        proofBitmap: Bitmap?
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4F6F5")
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val marginH = width * 0.06f
        val topOffset = width * 0.055f

        // Layered stacked paper edges behind top
        val layerBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE3E0")
        }
        val layerMidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E9EEEC")
        }
        canvas.drawRoundRect(
            RectF(marginH + 22f, topOffset - 16f, width - marginH - 22f, topOffset + 40f),
            18f, 18f, layerBackPaint
        )
        canvas.drawRoundRect(
            RectF(marginH + 11f, topOffset - 8f, width - marginH - 11f, topOffset + 40f),
            18f, 18f, layerMidPaint
        )

        // Main white paper slip
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val cardBottom = height - topOffset
        val paperRect = RectF(marginH, topOffset, width - marginH, cardBottom)
        canvas.drawRoundRect(paperRect, 20f, 20f, paperPaint)
        canvas.drawRoundRect(paperRect, 20f, 20f, borderPaint)

        val pad = width * 0.05f
        val leftX = marginH + pad
        val rightX = width - marginH - pad
        var y = topOffset + pad * 1.1f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F4C3A")
            textSize = width * 0.036f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = width * 0.022f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = width * 0.021f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = width * 0.024f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val amountValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F4C3A")
            textSize = width * 0.028f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1.6f
        }
        val headerRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F4C3A")
            strokeWidth = 2.5f
        }

        // Header
        canvas.drawText("CASH PAYMENT RECEIPT", width / 2f, y, titlePaint)
        y += width * 0.028f
        canvas.drawText(settings.businessName, width / 2f, y, subtitlePaint)
        y += width * 0.022f
        canvas.drawLine(leftX, y, rightX, y, headerRulePaint)
        y += width * 0.042f

        // Metadata row: Receipt No & Date
        val midGap = width * 0.04f
        val halfWidth = (rightX - leftX - midGap) / 2f
        val receiptLabel = "Receipt No:"
        val dateLabel = "Date:"

        canvas.drawText(receiptLabel, leftX, y, labelPaint)
        val rLabelW = labelPaint.measureText(receiptLabel) + 8f
        canvas.drawText(item.transaction.receiptNo, leftX + rLabelW, y, valuePaint)
        canvas.drawLine(leftX + rLabelW, y + 6f, leftX + halfWidth, y + 6f, rulePaint)

        val dateStartX = leftX + halfWidth + midGap
        canvas.drawText(dateLabel, dateStartX, y, labelPaint)
        val dLabelW = labelPaint.measureText(dateLabel) + 8f
        canvas.drawText(
            Formatters.formatDateSlash(item.transaction.transactionDateMillis),
            dateStartX + dLabelW,
            y,
            valuePaint
        )
        canvas.drawLine(dateStartX + dLabelW, y + 6f, rightX, y + 6f, rulePaint)

        y += width * 0.055f

        // 8 Form-style underlined rows
        val rows = buildReceiptRows(item, settings)
        val rowSpacing = width * 0.064f
        rows.forEachIndexed { idx, (label, value) ->
            val caption = "$label:"
            canvas.drawText(caption, leftX, y, labelPaint)
            val valPaint = if (idx == 2) amountValuePaint else valuePaint
            val labelW = labelPaint.measureText(caption) + 12f
            val availableW = rightX - (leftX + labelW)
            val clippedValue = clipTextToWidth(value, valPaint, availableW)
            canvas.drawText(clippedValue, leftX + labelW, y, valPaint)
            canvas.drawLine(leftX, y + 10f, rightX, y + 10f, rulePaint)
            y += rowSpacing
        }

        // Optional Proof Thumbnail if bitmap fits
        if (proofBitmap != null && y + width * 0.18f < cardBottom - pad) {
            y += width * 0.015f
            canvas.drawText("Attached Bill / Voucher Proof:", leftX, y, labelPaint)
            y += width * 0.018f
            val maxImgH = (cardBottom - pad - y).coerceAtMost(width * 0.22f)
            val maxImgW = rightX - leftX
            val scale = minOf(maxImgW / proofBitmap.width, maxImgH / proofBitmap.height)
            val drawW = proofBitmap.width * scale
            val drawH = proofBitmap.height * scale
            val destRect = RectF(leftX, y, leftX + drawW, y + drawH)
            canvas.drawBitmap(proofBitmap, null, destRect, null)
        }
    }

    private fun clipTextToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun loadProofBitmap(filePath: String?, mimeType: String?): Bitmap? {
        if (filePath.isNullOrBlank()) return null
        if (mimeType != null && !mimeType.startsWith("image/")) return null
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 1200 || opts.outHeight / sample > 1200) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createReceiptPdfFile(
        context: Context,
        item: TransactionWithParty,
        settings: BusinessSettings
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val proofBmp = loadProofBitmap(item.transaction.proofFilePath, item.transaction.proofMimeType)
        drawReceiptOnCanvas(page.canvas, pageWidth, pageHeight, item, settings, proofBmp)
        doc.finishPage(page)

        val outFile = File(exportsDir(context), "${item.transaction.receiptNo}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        proofBmp?.recycle()
        outFile
    }

    suspend fun createReceiptPngFile(
        context: Context,
        item: TransactionWithParty,
        settings: BusinessSettings
    ): File = withContext(Dispatchers.IO) {
        val proofBmp = loadProofBitmap(item.transaction.proofFilePath, item.transaction.proofMimeType)
        val width = 1080
        val height = if (proofBmp != null) 1420 else 1120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawReceiptOnCanvas(canvas, width, height, item, settings, proofBmp)

        val outFile = File(exportsDir(context), "${item.transaction.receiptNo}.png")
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        proofBmp?.recycle()
        outFile
    }

    fun printReceipt(
        context: Context,
        item: TransactionWithParty,
        settings: BusinessSettings
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "DZAED_${item.transaction.receiptNo}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("${item.transaction.receiptNo}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val doc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = doc.startPage(pageInfo)
                val proofBmp = loadProofBitmap(
                    item.transaction.proofFilePath,
                    item.transaction.proofMimeType
                )
                drawReceiptOnCanvas(page.canvas, 595, 842, item, settings, proofBmp)
                doc.finishPage(page)
                proofBmp?.recycle()
                try {
                    FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    suspend fun createMonthlyReportPdfFile(
        context: Context,
        periodLabel: String,
        summary: MonthlyCashFlowSummary,
        settings: BusinessSettings
    ): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F4C3A")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 11f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        val rowsPerPage = 24
        val chunks = summary.entries.chunked(rowsPerPage).ifEmpty { listOf(emptyList()) }

        chunks.forEachIndexed { pageIndex, entries ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = doc.startPage(pageInfo)
            val c = page.canvas
            var y = 46f
            val left = 36f
            val right = pageWidth - 36f

            c.drawText("${settings.businessName} — Monthly Ledger Report", left, y, titlePaint)
            y += 18f
            c.drawText("Period: $periodLabel", left, y, subPaint)
            y += 14f
            c.drawLine(left, y, right, y, linePaint)
            y += 20f

            if (pageIndex == 0) {
                c.drawText(
                    "Monthly Income: ${Formatters.formatExactMoney(summary.totalIncome, settings.currencySymbol)}   |   " +
                        "Monthly Expense: ${Formatters.formatExactMoney(summary.totalExpense, settings.currencySymbol)}   |   " +
                        "Net Cash Flow: ${Formatters.formatExactMoney(summary.netCashFlow, settings.currencySymbol)}",
                    left,
                    y,
                    boldPaint
                )
                y += 16f
                c.drawText(
                    "Units Delivered: ${summary.totalPcsDelivered} Pcs   |   Units Taken: ${summary.totalPcsTaken} Pcs   |   Entries: ${summary.entries.size}",
                    left,
                    y,
                    subPaint
                )
                y += 22f
                c.drawLine(left, y, right, y, linePaint)
                y += 16f
            }

            // Table headers
            c.drawText("Date", left, y, boldPaint)
            c.drawText("Receipt", left + 70f, y, boldPaint)
            c.drawText("Party / Company", left + 165f, y, boldPaint)
            c.drawText("Type", left + 335f, y, boldPaint)
            c.drawText("Pcs", left + 425f, y, boldPaint)
            c.drawText("Amount", left + 465f, y, boldPaint)
            y += 8f
            c.drawLine(left, y, right, y, linePaint)
            y += 16f

            entries.forEach { entry ->
                val tx = entry.transaction
                c.drawText(Formatters.formatDateSlash(tx.transactionDateMillis), left, y, bodyPaint)
                c.drawText(clipTextToWidth(tx.receiptNo, bodyPaint, 90f), left + 70f, y, bodyPaint)
                c.drawText(
                    clipTextToWidth(entry.party.companyName, bodyPaint, 162f),
                    left + 165f,
                    y,
                    bodyPaint
                )
                c.drawText(entry.type.shortLabel, left + 335f, y, bodyPaint)
                c.drawText(
                    if (tx.quantityPcs > 0) "${tx.quantityPcs}" else "—",
                    left + 425f,
                    y,
                    bodyPaint
                )
                c.drawText(
                    Formatters.formatMoney(tx.amount, settings.currencySymbol),
                    left + 465f,
                    y,
                    bodyPaint
                )
                y += 22f
            }

            doc.finishPage(page)
        }

        val safeName = periodLabel.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(exportsDir(context), "DZAED_Report_$safeName.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    suspend fun createMonthlyReportCsvFile(
        context: Context,
        periodLabel: String,
        summary: MonthlyCashFlowSummary,
        settings: BusinessSettings
    ): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("Receipt No,Date,Company Name,Person Name,Phone,Transaction Type,Item,Quantity (Pcs),Unit Price,Amount (${settings.currencySymbol}),Notes")
        summary.entries.forEach { entry ->
            val tx = entry.transaction
            val p = entry.party
            fun esc(s: String): String = "\"${s.replace("\"", "\"\"")}\""
            sb.appendLine(
                listOf(
                    esc(tx.receiptNo),
                    esc(Formatters.formatDateSlash(tx.transactionDateMillis)),
                    esc(p.companyName),
                    esc(p.personName),
                    esc(p.phoneNumber),
                    esc(entry.type.label),
                    esc(tx.itemName),
                    tx.quantityPcs.toString(),
                    tx.unitPrice.toString(),
                    tx.amount.toString(),
                    esc(tx.notes)
                ).joinToString(",")
            )
        }
        val safeName = periodLabel.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(exportsDir(context), "DZAED_Report_$safeName.csv")
        outFile.writeText(sb.toString())
        outFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    suspend fun copyFileToUri(context: Context, source: File, destUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                FileInputStream(source).use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    } ?: return@withContext false
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    fun openProofFile(context: Context, filePath: String, mimeType: String?) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open Bill / Voucher"))
        } catch (_: Exception) {
            // No viewer installed for this MIME type
        }
    }

    suspend fun createPartyWebPortalHtmlFile(
        context: Context,
        summary: PartySummary,
        transactions: List<LedgerTransactionEntity>,
        settings: BusinessSettings
    ): File = withContext(Dispatchers.IO) {
        val html = LocalLedgerWebServer.renderPartyStatementHtml(
            summary = summary,
            transactions = transactions,
            settings = settings,
            isLivePortal = false
        )
        val outFile = File(exportsDir(context), "${summary.party.username}_ledger.html")
        outFile.writeText(html)
        outFile
    }

    fun openUrlInBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // No browser installed
        }
    }
}
