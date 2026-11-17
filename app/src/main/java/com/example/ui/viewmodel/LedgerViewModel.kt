package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LedgerTransactionEntity
import com.example.data.local.PartyEntity
import com.example.data.local.TradeLedgerDatabase
import com.example.data.model.BusinessSettings
import com.example.data.model.MonthlyCashFlowSummary
import com.example.data.model.PartyBalanceFilter
import com.example.data.model.PartyRole
import com.example.data.model.PartySummary
import com.example.data.model.TransactionType
import com.example.data.model.TransactionWithParty
import com.example.data.preferences.BusinessSettingsDataStore
import com.example.data.repository.LedgerRepository
import com.example.util.Formatters
import com.example.util.ProofStorageHelper
import com.example.util.UsernameHelper
import com.example.web.LocalLedgerWebServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CashFlowFilter(
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthZeroBased: Int = Calendar.getInstance().get(Calendar.MONTH),
    val allMonths: Boolean = false,
    val selectedDayMillis: Long? = null,
    val transactionType: TransactionType? = null
) {
    val periodLabel: String
        get() = if (allMonths) {
            "All Months"
        } else if (selectedDayMillis != null) {
            Formatters.formatDateShort(selectedDayMillis)
        } else {
            Formatters.formatMonthYear(year, monthZeroBased)
        }
}

data class WebPortalState(
    val isRunning: Boolean = false,
    val port: Int = 0,
    val localIp: String? = null
) {
    val baseLocalUrl: String
        get() = if (port > 0) "http://127.0.0.1:$port" else ""

    val baseLanUrl: String?
        get() = if (port > 0 && !localIp.isNullOrBlank()) "http://$localIp:$port" else null

    fun partyUrl(username: String): String {
        val base = baseLanUrl ?: baseLocalUrl
        return if (base.isNotEmpty() && username.isNotBlank()) "$base/u/$username" else ""
    }

    fun partyLocalUrl(username: String): String {
        return if (baseLocalUrl.isNotEmpty() && username.isNotBlank()) "$baseLocalUrl/u/$username" else ""
    }
}

data class ShareableLinkSession(
    val username: String,
    val token: String,
    val deepLinkUrl: String,
    val webShareUrl: String,
    val expiresAtMillis: Long
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis > expiresAtMillis
}

sealed interface ShareableViewUiState {
    data object Closed : ShareableViewUiState
    data class Active(
        val username: String,
        val partySummary: PartySummary?,
        val transactions: List<LedgerTransactionEntity>,
        val deepLinkUrl: String,
        val expiresAtMillis: Long?,
        val isExpired: Boolean,
        val notFound: Boolean
    ) : ShareableViewUiState
}

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TradeLedgerDatabase.getInstance(application)
    private val repository = LedgerRepository(db.ledgerDao())
    private val settingsStore = BusinessSettingsDataStore(application)

    val businessSettings: StateFlow<BusinessSettings> = settingsStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = BusinessSettings()
        )

    private val activeShareTokens = ConcurrentHashMap<String, ShareableLinkSession>()

    private val webServer = LocalLedgerWebServer(
        repository = repository,
        settingsProvider = { businessSettings.value },
        tokenValidator = { username, token ->
            val clean = UsernameHelper.sanitizeUsername(username)
            val session = activeShareTokens[token]
            session != null &&
                session.username.equals(clean, ignoreCase = true) &&
                !session.isExpired()
        }
    )

    private val _webPortalState = MutableStateFlow(WebPortalState())
    val webPortalState: StateFlow<WebPortalState> = _webPortalState.asStateFlow()

    private val _shareableTarget = MutableStateFlow<ShareableTarget?>(null)

    private data class ShareableTarget(
        val username: String,
        val deepLinkUrl: String,
        val expiresAtMillis: Long?,
        val isExpired: Boolean
    )

    init {
        startWebPortal()
    }

    fun startWebPortal() {
        val port = webServer.start(8765)
        val ip = LocalLedgerWebServer.getLocalIpv4Address()
        _webPortalState.value = WebPortalState(
            isRunning = webServer.isRunning,
            port = port,
            localIp = ip
        )
    }

    fun stopWebPortal() {
        webServer.stop()
        _webPortalState.value = WebPortalState(isRunning = false, port = 0, localIp = null)
    }

    override fun onCleared() {
        super.onCleared()
        webServer.stop()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _balanceFilter = MutableStateFlow(PartyBalanceFilter.ALL)
    val balanceFilter: StateFlow<PartyBalanceFilter> = _balanceFilter.asStateFlow()

    private val _selectedPartyId = MutableStateFlow<Long?>(null)
    val selectedPartyId: StateFlow<Long?> = _selectedPartyId.asStateFlow()

    private val _cashFlowFilter = MutableStateFlow(CashFlowFilter())
    val cashFlowFilter: StateFlow<CashFlowFilter> = _cashFlowFilter.asStateFlow()

    private val _receiptSearchQuery = MutableStateFlow("")
    val receiptSearchQuery: StateFlow<String> = _receiptSearchQuery.asStateFlow()

    private val _activeReceipt = MutableStateFlow<TransactionWithParty?>(null)
    val activeReceipt: StateFlow<TransactionWithParty?> = _activeReceipt.asStateFlow()

    val allPartySummaries: StateFlow<List<PartySummary>> = repository.partySummariesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val shareableViewState: StateFlow<ShareableViewUiState> = combine(
        _shareableTarget,
        repository.partySummariesFlow
    ) { target, summaries ->
        target to summaries
    }.flatMapLatest { (target, summaries) ->
        if (target == null) {
            flowOf(ShareableViewUiState.Closed)
        } else {
            val matchedSummary = summaries.firstOrNull {
                it.party.username.equals(target.username, ignoreCase = true)
            }
            if (matchedSummary == null) {
                flowOf(
                    ShareableViewUiState.Active(
                        username = target.username,
                        partySummary = null,
                        transactions = emptyList(),
                        deepLinkUrl = target.deepLinkUrl,
                        expiresAtMillis = target.expiresAtMillis,
                        isExpired = target.isExpired,
                        notFound = true
                    )
                )
            } else {
                repository.observePartyDetail(matchedSummary.party.id).flatMapLatest { (summary, txs) ->
                    flowOf(
                        ShareableViewUiState.Active(
                            username = target.username,
                            partySummary = summary ?: matchedSummary,
                            transactions = txs,
                            deepLinkUrl = target.deepLinkUrl,
                            expiresAtMillis = target.expiresAtMillis,
                            isExpired = target.isExpired,
                            notFound = false
                        )
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ShareableViewUiState.Closed
    )

    val filteredParties: StateFlow<List<PartySummary>> = combine(
        repository.partySummariesFlow,
        _searchQuery,
        _balanceFilter
    ) { parties, query, filter ->
        val trimmed = query.trim().lowercase()
        val cleanHandle = trimmed.removePrefix("@")
        parties.filter { item ->
            val matchesQuery = trimmed.isEmpty() ||
                item.party.companyName.lowercase().contains(trimmed) ||
                item.party.personName.lowercase().contains(trimmed) ||
                item.party.phoneNumber.lowercase().contains(trimmed) ||
                item.party.username.lowercase().contains(cleanHandle)
            val matchesFilter = when (filter) {
                PartyBalanceFilter.ALL -> true
                PartyBalanceFilter.RECEIVABLE -> item.netBalance > 0.005
                PartyBalanceFilter.PAYABLE -> item.netBalance < -0.005
                PartyBalanceFilter.SETTLED -> kotlin.math.abs(item.netBalance) <= 0.005
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPartyDetail: StateFlow<Pair<PartySummary?, List<LedgerTransactionEntity>>> =
        _selectedPartyId.flatMapLatest { id ->
            if (id == null) flowOf(null to emptyList())
            else repository.observePartyDetail(id)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null to emptyList()
        )

    val monthlyCashFlowSummary: StateFlow<MonthlyCashFlowSummary> = combine(
        repository.allTransactionsWithPartyFlow,
        _cashFlowFilter
    ) { allEntries, filter ->
        val filtered = allEntries.filter { entry ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = entry.transaction.transactionDateMillis
            }
            val matchesMonth = filter.allMonths || (
                cal.get(Calendar.YEAR) == filter.year &&
                    cal.get(Calendar.MONTH) == filter.monthZeroBased
                )
            val matchesDay = filter.selectedDayMillis == null || run {
                val dayCal = Calendar.getInstance().apply { timeInMillis = filter.selectedDayMillis }
                cal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
            }
            val matchesType = filter.transactionType == null || entry.type == filter.transactionType
            matchesMonth && matchesDay && matchesType
        }

        val periodEntries = allEntries.filter { entry ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = entry.transaction.transactionDateMillis
            }
            val matchesMonth = filter.allMonths || (
                cal.get(Calendar.YEAR) == filter.year &&
                    cal.get(Calendar.MONTH) == filter.monthZeroBased
                )
            val matchesDay = filter.selectedDayMillis == null || run {
                val dayCal = Calendar.getInstance().apply { timeInMillis = filter.selectedDayMillis }
                cal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
            }
            matchesMonth && matchesDay
        }

        var income = 0.0
        var expense = 0.0
        var pcsDelivered = 0
        var pcsTaken = 0
        var salesVal = 0.0
        var purchaseVal = 0.0

        for (entry in periodEntries) {
            when (entry.type) {
                TransactionType.MONEY_RECEIVED -> income += entry.transaction.amount
                TransactionType.MONEY_GIVEN -> expense += entry.transaction.amount
                TransactionType.PRODUCT_GIVEN -> {
                    pcsDelivered += entry.transaction.quantityPcs
                    salesVal += entry.transaction.amount
                }
                TransactionType.PRODUCT_TAKEN -> {
                    pcsTaken += entry.transaction.quantityPcs
                    purchaseVal += entry.transaction.amount
                }
            }
        }

        MonthlyCashFlowSummary(
            totalIncome = income,
            totalExpense = expense,
            totalPcsDelivered = pcsDelivered,
            totalPcsTaken = pcsTaken,
            totalSalesValue = salesVal,
            totalPurchaseValue = purchaseVal,
            entries = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MonthlyCashFlowSummary(0.0, 0.0, 0, 0, 0.0, 0.0, emptyList())
    )

    val allReceipts: StateFlow<List<TransactionWithParty>> = combine(
        repository.allTransactionsWithPartyFlow,
        _receiptSearchQuery
    ) { all, q ->
        val trimmed = q.trim().lowercase()
        val cleanHandle = trimmed.removePrefix("@")
        if (trimmed.isEmpty()) all
        else all.filter {
            it.transaction.receiptNo.lowercase().contains(trimmed) ||
                it.party.companyName.lowercase().contains(trimmed) ||
                it.party.personName.lowercase().contains(trimmed) ||
                it.party.username.lowercase().contains(cleanHandle) ||
                it.transaction.notes.lowercase().contains(trimmed) ||
                it.transaction.itemName.lowercase().contains(trimmed)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun generateShareableLink(
        rawUsername: String,
        ttlMinutes: Long = 60L
    ): ShareableLinkSession {
        val cleanUsername = UsernameHelper.sanitizeUsername(rawUsername)
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val expiresAt = System.currentTimeMillis() + ttlMinutes * 60_000L
        val deepLink = "tradeledger://share?u=$cleanUsername&token=$token&exp=$expiresAt"
        val portal = _webPortalState.value
        val base = portal.baseLanUrl ?: portal.baseLocalUrl
        val webShareUrl = if (base.isNotBlank()) {
            "$base/share?u=$cleanUsername&token=$token"
        } else ""

        val session = ShareableLinkSession(
            username = cleanUsername,
            token = token,
            deepLinkUrl = deepLink,
            webShareUrl = webShareUrl,
            expiresAtMillis = expiresAt
        )
        activeShareTokens[token] = session
        return session
    }

    fun openShareableView(session: ShareableLinkSession) {
        _shareableTarget.value = ShareableTarget(
            username = session.username,
            deepLinkUrl = session.deepLinkUrl,
            expiresAtMillis = session.expiresAtMillis,
            isExpired = session.isExpired()
        )
    }

    fun openShareableViewByUsername(
        rawUsername: String,
        ttlMinutes: Long = 60L
    ): ShareableLinkSession {
        val session = generateShareableLink(rawUsername, ttlMinutes)
        openShareableView(session)
        return session
    }

    fun handleIncomingDeepLink(uri: Uri?) {
        if (uri == null || uri.scheme != "tradeledger") return
        val cleanUsername = when (uri.host) {
            "share" -> UsernameHelper.sanitizeUsername(uri.getQueryParameter("u").orEmpty())
            "u" -> UsernameHelper.sanitizeUsername(
                uri.pathSegments.firstOrNull() ?: uri.getQueryParameter("u").orEmpty()
            )
            else -> ""
        }
        if (cleanUsername.isEmpty()) return
        val exp = uri.getQueryParameter("exp")?.toLongOrNull()
        val isExpired = exp != null && System.currentTimeMillis() > exp
        _shareableTarget.value = ShareableTarget(
            username = cleanUsername,
            deepLinkUrl = uri.toString(),
            expiresAtMillis = exp,
            isExpired = isExpired
        )
    }

    fun closeShareableView() {
        _shareableTarget.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setBalanceFilter(filter: PartyBalanceFilter) {
        _balanceFilter.value = filter
    }

    fun selectParty(partyId: Long?) {
        _selectedPartyId.value = partyId
    }

    fun setReceiptSearchQuery(query: String) {
        _receiptSearchQuery.value = query
    }

    fun previousMonth() {
        val current = _cashFlowFilter.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, current.year)
            set(Calendar.MONTH, current.monthZeroBased)
            add(Calendar.MONTH, -1)
        }
        _cashFlowFilter.value = current.copy(
            year = cal.get(Calendar.YEAR),
            monthZeroBased = cal.get(Calendar.MONTH),
            allMonths = false,
            selectedDayMillis = null
        )
    }

    fun nextMonth() {
        val current = _cashFlowFilter.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, current.year)
            set(Calendar.MONTH, current.monthZeroBased)
            add(Calendar.MONTH, 1)
        }
        _cashFlowFilter.value = current.copy(
            year = cal.get(Calendar.YEAR),
            monthZeroBased = cal.get(Calendar.MONTH),
            allMonths = false,
            selectedDayMillis = null
        )
    }

    fun toggleAllMonths(allMonths: Boolean) {
        _cashFlowFilter.value = _cashFlowFilter.value.copy(
            allMonths = allMonths,
            selectedDayMillis = if (allMonths) null else _cashFlowFilter.value.selectedDayMillis
        )
    }

    fun setSpecificDateFilter(dayMillis: Long?) {
        if (dayMillis == null) {
            _cashFlowFilter.value = _cashFlowFilter.value.copy(selectedDayMillis = null)
        } else {
            val cal = Calendar.getInstance().apply { timeInMillis = dayMillis }
            _cashFlowFilter.value = _cashFlowFilter.value.copy(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH),
                allMonths = false,
                selectedDayMillis = dayMillis
            )
        }
    }

    fun setTransactionTypeFilter(type: TransactionType?) {
        _cashFlowFilter.value = _cashFlowFilter.value.copy(transactionType = type)
    }

    fun addParty(
        companyName: String,
        personName: String,
        phoneNumber: String,
        address: String,
        role: PartyRole,
        desiredUsername: String,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.addParty(
                companyName = companyName,
                personName = personName,
                phoneNumber = phoneNumber,
                address = address,
                role = role,
                desiredUsername = desiredUsername
            )
            onCreated(id)
        }
    }

    fun updateParty(
        existing: PartyEntity,
        companyName: String,
        personName: String,
        phoneNumber: String,
        address: String,
        role: PartyRole,
        desiredUsername: String
    ) {
        viewModelScope.launch {
            repository.updateParty(
                existing = existing,
                companyName = companyName,
                personName = personName,
                phoneNumber = phoneNumber,
                address = address,
                role = role,
                desiredUsername = desiredUsername
            )
        }
    }

    fun deleteParty(partyId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteParty(partyId)
            if (_selectedPartyId.value == partyId) {
                _selectedPartyId.value = null
            }
            onDeleted()
        }
    }

    fun recordTransaction(
        partyId: Long,
        type: TransactionType,
        transactionDateMillis: Long,
        itemName: String,
        quantityPcs: Int,
        unitPrice: Double,
        amount: Double,
        notes: String,
        pickedProofUri: Uri?,
        onSavedShowReceipt: (TransactionWithParty) -> Unit = {}
    ) {
        viewModelScope.launch {
            val copiedProof = pickedProofUri?.let {
                ProofStorageHelper.copyProofToInternalStorage(getApplication(), it)
            }
            val created = repository.recordTransaction(
                partyId = partyId,
                type = type,
                transactionDateMillis = transactionDateMillis,
                itemName = itemName,
                quantityPcs = quantityPcs,
                unitPrice = unitPrice,
                amount = amount,
                notes = notes,
                proofFilePath = copiedProof?.filePath,
                proofMimeType = copiedProof?.mimeType,
                proofDisplayName = copiedProof?.displayName
            )
            if (created != null) {
                val summary = allPartySummaries.value.firstOrNull { it.party.id == partyId }
                val latestBalance = if (summary != null) {
                    val delta = when (type) {
                        TransactionType.PRODUCT_GIVEN, TransactionType.MONEY_GIVEN -> amount
                        TransactionType.PRODUCT_TAKEN, TransactionType.MONEY_RECEIVED -> -amount
                    }
                    summary.netBalance + delta
                } else {
                    when (type) {
                        TransactionType.PRODUCT_GIVEN, TransactionType.MONEY_GIVEN -> amount
                        TransactionType.PRODUCT_TAKEN, TransactionType.MONEY_RECEIVED -> -amount
                    }
                }
                val enriched = created.copy(partyCurrentNetBalance = latestBalance)
                _activeReceipt.value = enriched
                onSavedShowReceipt(enriched)
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
        }
    }

    fun openReceipt(transactionWithParty: TransactionWithParty) {
        _activeReceipt.value = transactionWithParty
    }

    fun openReceiptForPartyTransaction(tx: LedgerTransactionEntity, summary: PartySummary) {
        _activeReceipt.value = TransactionWithParty(
            transaction = tx,
            party = summary.party,
            partyCurrentNetBalance = summary.netBalance
        )
    }

    fun dismissReceipt() {
        _activeReceipt.value = null
    }

    fun updateBusinessSettings(
        businessName: String,
        currencySymbol: String,
        businessPhone: String,
        businessAddress: String
    ) {
        viewModelScope.launch {
            settingsStore.updateSettings(
                businessName = businessName,
                currencySymbol = currencySymbol,
                businessPhone = businessPhone,
                businessAddress = businessAddress
            )
        }
    }
}
