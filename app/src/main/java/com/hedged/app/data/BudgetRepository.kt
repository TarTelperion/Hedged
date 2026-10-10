package com.hedged.app.data

import android.content.ContentResolver
import android.net.Uri
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate

data class BudgetData(
    val categories: List<HedgedDatabase.CategoryEntity> = emptyList(),
    val transactions: List<HedgedDatabase.TransactionEntity> = emptyList(),
    val budgets: List<HedgedDatabase.BudgetEntity> = emptyList(),
    val goals: List<HedgedDatabase.GoalEntity> = emptyList(),
    val goalEntries: List<HedgedDatabase.GoalEntryEntity> = emptyList(),
    val ready: Boolean = false,
)

data class BackupPayload(
    val format: String = "hedged-backup",
    val version: Int = 1,
    val exportedAtEpochMillis: Long = System.currentTimeMillis(),
    val categories: List<HedgedDatabase.CategoryEntity>,
    val transactions: List<HedgedDatabase.TransactionEntity>,
    val budgets: List<HedgedDatabase.BudgetEntity>,
    val goals: List<HedgedDatabase.GoalEntity>,
    val goalEntries: List<HedgedDatabase.GoalEntryEntity>,
)

class BudgetRepository(private val db: HedgedDatabase) {
    private val _data = MutableStateFlow(BudgetData())
    val data: StateFlow<BudgetData> = _data.asStateFlow()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (db.categoryDao().count() == 0) {
            val defaults = listOf(
                category("General Income", "INCOME", "mint", 0),
                category("Groceries", "EXPENSE", "sage", 1),
                category("Life Happens", "EXPENSE", "amber", 2),
                category("Tithing", "EXPENSE", "blue", 3),
                category("Housing", "EXPENSE", "violet", 4),
                category("Transport", "EXPENSE", "coral", 5),
            )
            db.categoryDao().insertAll(defaults)
        }
        if (db.goalDao().count() == 0) {
            db.goalDao().insert(HedgedDatabase.GoalEntity(0, "Car Fund", 900_000, "mint", true, 0))
        }
        reloadBlocking()
    }

    suspend fun addTransaction(
        categoryId: Long,
        type: String,
        amountCents: Long,
        date: LocalDate,
        description: String?,
        notes: String?,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.transactionDao().insert(
            HedgedDatabase.TransactionEntity(
                0,
                date.toEpochDay(),
                categoryId,
                type,
                amountCents,
                description?.trim()?.takeIf { it.isNotEmpty() },
                notes?.trim()?.takeIf { it.isNotEmpty() },
                now,
                now,
            )
        )
        reloadBlocking()
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        db.transactionDao().deleteById(id)
        reloadBlocking()
    }

    suspend fun saveCategory(
        existingId: Long?,
        name: String,
        kind: String,
        colorKey: String,
    ) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext
        val existing = _data.value.categories.firstOrNull { it.id == existingId }
        if (existing == null) {
            val order = (_data.value.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
            db.categoryDao().insert(category(trimmed, kind, colorKey, order))
        } else {
            existing.name = trimmed
            existing.kind = kind
            existing.colorKey = colorKey
            existing.isActive = true
            db.categoryDao().update(existing)
        }
        reloadBlocking()
    }

    suspend fun setCategoryActive(id: Long, active: Boolean) = withContext(Dispatchers.IO) {
        val category = db.categoryDao().getAll().firstOrNull { it.id == id } ?: return@withContext
        category.isActive = active
        db.categoryDao().update(category)
        reloadBlocking()
    }

    suspend fun saveBudget(
        monthKey: String,
        categoryId: Long,
        desiredCapCents: Long?,
        fundedCents: Long,
        ruleType: String,
        rateBasisPoints: Int?,
    ) = withContext(Dispatchers.IO) {
        val existing = db.budgetDao().getForMonth(monthKey).firstOrNull { it.categoryId == categoryId }
        db.budgetDao().insert(
            HedgedDatabase.BudgetEntity(
                existing?.id ?: 0,
                monthKey,
                categoryId,
                desiredCapCents,
                fundedCents,
                ruleType,
                rateBasisPoints,
            )
        )
        reloadBlocking()
    }

    suspend fun copyBudgets(fromMonth: String, toMonth: String) = withContext(Dispatchers.IO) {
        if (db.budgetDao().getForMonth(toMonth).isEmpty()) {
            db.budgetDao().getForMonth(fromMonth).forEach { source ->
                db.budgetDao().insert(
                    HedgedDatabase.BudgetEntity(
                        0,
                        toMonth,
                        source.categoryId,
                        source.desiredCapCents,
                        source.fundedCents,
                        source.ruleType,
                        source.rateBasisPoints,
                    )
                )
            }
        }
        reloadBlocking()
    }

    suspend fun saveGoal(existingId: Long?, name: String, targetCents: Long, colorKey: String) =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isBlank() || targetCents <= 0) return@withContext
            val existing = db.goalDao().getAll().firstOrNull { it.id == existingId }
            if (existing == null) {
                val order = (_data.value.goals.maxOfOrNull { it.sortOrder } ?: 0) + 1
                db.goalDao().insert(HedgedDatabase.GoalEntity(0, trimmed, targetCents, colorKey, true, order))
            } else {
                existing.name = trimmed
                existing.targetCents = targetCents
                existing.colorKey = colorKey
                existing.isActive = true
                db.goalDao().update(existing)
            }
            reloadBlocking()
        }

    suspend fun addGoalEntry(goalId: Long, amountCentsSigned: Long, notes: String?) =
        withContext(Dispatchers.IO) {
            if (amountCentsSigned == 0L) return@withContext
            db.goalEntryDao().insert(
                HedgedDatabase.GoalEntryEntity(
                    0,
                    goalId,
                    LocalDate.now().toEpochDay(),
                    amountCentsSigned,
                    notes?.trim()?.takeIf { it.isNotEmpty() },
                    System.currentTimeMillis(),
                )
            )
            reloadBlocking()
        }

    suspend fun exportBackup(resolver: ContentResolver, uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = currentBackup()
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            OutputStreamWriter(stream).use { writer -> gson.toJson(snapshot, writer) }
        } ?: error("Could not open backup file")
    }

    suspend fun restoreBackup(resolver: ContentResolver, uri: Uri) = withContext(Dispatchers.IO) {
        val payload = resolver.openInputStream(uri)?.use { stream ->
            stream.reader().use { reader -> gson.fromJson(reader, BackupPayload::class.java) }
        } ?: error("Could not open backup file")
        require(payload.format == "hedged-backup" && payload.version == 1) { "This is not a supported Hedged backup" }
        db.runInTransaction {
            db.goalEntryDao().deleteAll()
            db.transactionDao().deleteAll()
            db.budgetDao().deleteAll()
            db.goalDao().deleteAll()
            db.categoryDao().deleteAll()
            db.categoryDao().insertAll(payload.categories)
            db.goalDao().insertAll(payload.goals)
            db.budgetDao().insertAll(payload.budgets)
            db.transactionDao().insertAll(payload.transactions)
            db.goalEntryDao().insertAll(payload.goalEntries)
        }
        reloadBlocking()
    }

    private fun currentBackup(): BackupPayload = BackupPayload(
        categories = db.categoryDao().getAll(),
        transactions = db.transactionDao().getAll(),
        budgets = db.budgetDao().getAll(),
        goals = db.goalDao().getAll(),
        goalEntries = db.goalEntryDao().getAll(),
    )

    private fun reloadBlocking() {
        _data.value = BudgetData(
            categories = db.categoryDao().getAll(),
            transactions = db.transactionDao().getAll(),
            budgets = db.budgetDao().getAll(),
            goals = db.goalDao().getAll(),
            goalEntries = db.goalEntryDao().getAll(),
            ready = true,
        )
    }

    private fun category(name: String, kind: String, colorKey: String, order: Int) =
        HedgedDatabase.CategoryEntity(0, name, kind, colorKey, true, order)
}
