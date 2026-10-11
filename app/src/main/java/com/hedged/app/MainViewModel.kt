package com.hedged.app

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hedged.app.data.BudgetData
import com.hedged.app.data.BudgetRepository
import com.hedged.app.data.HedgedDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BudgetRepository = (application as HedgedApplication).repository
    val data: StateFlow<BudgetData> = repository.data

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { repository.initialize() }
    }

    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun currentMonth() { _selectedMonth.value = YearMonth.now() }
    fun clearMessage() { _message.value = null }

    fun addTransaction(
        categoryId: Long,
        type: String,
        amountCents: Long,
        date: LocalDate,
        description: String?,
        notes: String?,
    ) = launchAction("Saved") {
        repository.addTransaction(categoryId, type, amountCents, date, description, notes)
    }

    fun deleteTransaction(id: Long) = launchAction("Transaction deleted") {
        repository.deleteTransaction(id)
    }

    fun updateTransaction(
        id: Long,
        categoryId: Long,
        type: String,
        amountCents: Long,
        date: LocalDate,
        description: String?,
        notes: String?,
    ) = launchAction("Transaction updated") {
        repository.updateTransaction(id, categoryId, type, amountCents, date, description, notes)
    }

    fun saveCategory(existingId: Long?, name: String, kind: String, colorKey: String) =
        launchAction("Category saved") { repository.saveCategory(existingId, name, kind, colorKey) }

    fun setCategoryActive(id: Long, active: Boolean) = launchAction(if (active) "Category restored" else "Category archived") {
        repository.setCategoryActive(id, active)
    }

    fun saveBudget(
        categoryId: Long,
        desiredCapCents: Long?,
        fundedCents: Long,
        ruleType: String,
        rateBasisPoints: Int?,
    ) = launchAction("Budget updated") {
        repository.saveBudget(_selectedMonth.value.toString(), categoryId, desiredCapCents, fundedCents, ruleType, rateBasisPoints)
    }

    fun copyPreviousMonth() = launchAction("Previous budget copied") {
        val current = _selectedMonth.value
        repository.copyBudgets(current.minusMonths(1).toString(), current.toString())
    }

    fun saveGoal(existingId: Long?, name: String, targetCents: Long, colorKey: String) =
        launchAction("Goal saved") { repository.saveGoal(existingId, name, targetCents, colorKey) }

    fun addGoalEntry(goalId: Long, amountCentsSigned: Long, notes: String?) =
        launchAction("Goal updated") { repository.addGoalEntry(goalId, amountCentsSigned, notes) }

    fun exportBackup(resolver: ContentResolver, uri: Uri) = launchAction("Backup exported") {
        repository.exportBackup(resolver, uri)
    }

    fun restoreBackup(resolver: ContentResolver, uri: Uri) = launchAction("Backup restored") {
        repository.restoreBackup(resolver, uri)
    }

    private fun launchAction(successMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _message.value = successMessage }
                .onFailure { _message.value = it.message ?: "Something went wrong" }
        }
    }
}
