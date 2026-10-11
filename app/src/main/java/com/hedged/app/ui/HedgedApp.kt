@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hedged.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hedged.app.MainViewModel
import com.hedged.app.data.BudgetData
import com.hedged.app.data.HedgedDatabase
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.max

private enum class AppScreen(val label: String, val mark: String) {
    HOME("Home", "⌂"), PLAN("Plan", "◫"), ACTIVITY("Activity", "↕"), SETTINGS("Settings", "···")
}

private data class CategoryBudgetRow(
    val category: HedgedDatabase.CategoryEntity,
    val budget: HedgedDatabase.BudgetEntity?,
    val fundedCents: Long,
    val spentCents: Long,
) {
    val spendingLimitCents: Long get() = budget?.desiredCapCents?.takeIf { it > 0 } ?: fundedCents
    val remainingCents: Long get() = spendingLimitCents - spentCents
    val progress: Float get() = if (spendingLimitCents <= 0) 0f else (spentCents.toFloat() / spendingLimitCents).coerceIn(0f, 1f)
    val status: String get() = when {
        spendingLimitCents <= 0L -> "Not funded"
        spentCents > spendingLimitCents -> "Over budget"
        spentCents >= spendingLimitCents * 0.8 -> "Near limit"
        else -> "On track"
    }
}

private data class GoalRow(
    val goal: HedgedDatabase.GoalEntity,
    val balanceCents: Long,
) {
    val progress: Float get() = if (goal.targetCents <= 0) 0f else (balanceCents.toFloat() / goal.targetCents).coerceIn(0f, 1f)
}

private data class MonthSnapshot(
    val incomeCents: Long,
    val expenseCents: Long,
    val goalContributionsCents: Long,
    val allocatedCents: Long,
    val unallocatedCents: Long,
    val categoryRows: List<CategoryBudgetRow>,
    val goalRows: List<GoalRow>,
    val transactions: List<HedgedDatabase.TransactionEntity>,
)

private data class CashFlowPoint(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long,
)

@Composable
fun HedgedApp(viewModel: MainViewModel) {
    HedgedTheme {
        val data by viewModel.data.collectAsStateWithLifecycle()
        val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
        var showAddTransaction by rememberSaveable { mutableStateOf(false) }
        var editingTransaction by remember { mutableStateOf<HedgedDatabase.TransactionEntity?>(null) }
        var showCashFlow by rememberSaveable { mutableStateOf(false) }
        var editingCategory by remember { mutableStateOf<HedgedDatabase.CategoryEntity?>(null) }
        var addingCategory by rememberSaveable { mutableStateOf(false) }
        var editingBudget by remember { mutableStateOf<HedgedDatabase.CategoryEntity?>(null) }
        var editingGoal by remember { mutableStateOf<HedgedDatabase.GoalEntity?>(null) }
        var addingGoal by rememberSaveable { mutableStateOf(false) }
        var fundingGoal by remember { mutableStateOf<HedgedDatabase.GoalEntity?>(null) }
        val snackbarHost = remember { SnackbarHostState() }
        val snapshot = remember(data, month) { data.snapshot(month) }

        LaunchedEffect(message) {
            message?.let {
                snackbarHost.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        if (!data.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHost) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        AppScreen.entries.forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Text(item.mark, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (screen != AppScreen.SETTINGS) {
                        FloatingActionButton(
                            onClick = { showAddTransaction = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                        ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                    }
                },
            ) { padding ->
                AnimatedContent(screen, label = "screen") { destination ->
                    when (destination) {
                        AppScreen.HOME -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            month = month,
                            snapshot = snapshot,
                            data = data,
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            onCurrentMonth = viewModel::currentMonth,
                            onCashFlow = { showCashFlow = true },
                            onPlan = { screen = AppScreen.PLAN },
                            onGoal = { fundingGoal = it },
                        )
                        AppScreen.PLAN -> PlanScreen(
                            modifier = Modifier.padding(padding),
                            month = month,
                            snapshot = snapshot,
                            data = data,
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            onEditBudget = { editingBudget = it },
                            onCopyPrevious = viewModel::copyPreviousMonth,
                            onAddGoal = { addingGoal = true },
                            onEditGoal = { editingGoal = it },
                            onFundGoal = { fundingGoal = it },
                        )
                        AppScreen.ACTIVITY -> ActivityScreen(
                            modifier = Modifier.padding(padding),
                            month = month,
                            snapshot = snapshot,
                            data = data,
                            onPreviousMonth = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            onEdit = { editingTransaction = it },
                            onDelete = viewModel::deleteTransaction,
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            modifier = Modifier.padding(padding),
                            data = data,
                            viewModel = viewModel,
                            onAddCategory = { addingCategory = true },
                            onEditCategory = { editingCategory = it },
                        )
                    }
                }
            }
        }

        if (showAddTransaction || editingTransaction != null) {
            val transaction = editingTransaction
            TransactionSheet(
                existing = transaction,
                categories = data.categories.filter { it.isActive || it.id == transaction?.categoryId },
                onDismiss = {
                    showAddTransaction = false
                    editingTransaction = null
                },
                onSave = { id, category, amount, date, description, notes ->
                    if (id == null) {
                        viewModel.addTransaction(category.id, category.kind, amount, date, description, notes)
                    } else {
                        viewModel.updateTransaction(id, category.id, category.kind, amount, date, description, notes)
                    }
                    showAddTransaction = false
                    editingTransaction = null
                },
            )
        }

        if (showCashFlow) {
            CashFlowSheet(
                data = data,
                endMonth = month,
                onDismiss = { showCashFlow = false },
            )
        }

        if (addingCategory || editingCategory != null) {
            CategorySheet(
                existing = editingCategory,
                onDismiss = { addingCategory = false; editingCategory = null },
                onSave = { id, name, kind, color ->
                    viewModel.saveCategory(id, name, kind, color)
                    addingCategory = false
                    editingCategory = null
                },
            )
        }

        editingBudget?.let { category ->
            BudgetSheet(
                category = category,
                existing = data.budgets.firstOrNull { it.monthKey == month.toString() && it.categoryId == category.id },
                incomeCents = snapshot.incomeCents,
                onDismiss = { editingBudget = null },
                onSave = { cap, funded, rule, rate ->
                    viewModel.saveBudget(category.id, cap, funded, rule, rate)
                    editingBudget = null
                },
            )
        }

        if (addingGoal || editingGoal != null) {
            GoalSheet(
                existing = editingGoal,
                onDismiss = { addingGoal = false; editingGoal = null },
                onSave = { id, name, target, color ->
                    viewModel.saveGoal(id, name, target, color)
                    addingGoal = false
                    editingGoal = null
                },
            )
        }

        fundingGoal?.let { goal ->
            GoalEntrySheet(
                goal = goal,
                balanceCents = snapshot.goalRows.firstOrNull { it.goal.id == goal.id }?.balanceCents ?: 0,
                onDismiss = { fundingGoal = null },
                onSave = { amount, note ->
                    viewModel.addGoalEntry(goal.id, amount, note)
                    fundingGoal = null
                },
            )
        }
    }
}

private fun BudgetData.snapshot(month: YearMonth): MonthSnapshot {
    val start = month.atDay(1).toEpochDay()
    val end = month.plusMonths(1).atDay(1).toEpochDay()
    val monthTransactions = transactions.filter { it.dateEpochDay in start until end }
    val income = monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amountCents }
    val expenses = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
    val monthBudgets = budgets.filter { it.monthKey == month.toString() }.associateBy { it.categoryId }
    val expenseCategories = categories.filter { it.isActive && it.kind == "EXPENSE" }
    val rows = expenseCategories.map { category ->
        val budget = monthBudgets[category.id]
        val funded = if (budget?.ruleType == "PERCENT") {
            income * (budget.rateBasisPoints ?: 0) / 10_000
        } else budget?.fundedCents ?: 0
        val spent = monthTransactions.filter { it.type == "EXPENSE" && it.categoryId == category.id }.sumOf { it.amountCents }
        CategoryBudgetRow(category, budget, funded, spent)
    }
    val goalRows = goals.filter { it.isActive }.map { goal ->
        GoalRow(goal, goalEntries.filter { it.goalId == goal.id }.sumOf { it.amountCentsSigned })
    }
    val goalContribution = goalEntries.filter { it.dateEpochDay in start until end && it.amountCentsSigned > 0 }.sumOf { it.amountCentsSigned }
    val allocated = rows.sumOf { it.fundedCents } + goalContribution
    return MonthSnapshot(
        incomeCents = income,
        expenseCents = expenses,
        goalContributionsCents = goalContribution,
        allocatedCents = allocated,
        unallocatedCents = income - allocated,
        categoryRows = rows,
        goalRows = goalRows,
        transactions = monthTransactions,
    )
}

@Composable
private fun AppHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        trailing?.invoke()
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    TextButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(56.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(150.dp),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        onClick = onNext,
                        modifier = Modifier.size(56.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
                }
                if (onToday != null && month != YearMonth.now()) {
                    FilledTonalButton(
                        onClick = onToday,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("This month") }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    month: YearMonth,
    snapshot: MonthSnapshot,
    data: BudgetData,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onCashFlow: () -> Unit,
    onPlan: () -> Unit,
    onGoal: (HedgedDatabase.GoalEntity) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AppHeader("Hedged")
        }
        item { MonthSwitcher(month, onPreviousMonth, onNextMonth, onCurrentMonth) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Evergreen, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCashFlow),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("NET CASH FLOW", color = Color.White.copy(alpha = .66f), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(money(snapshot.incomeCents - snapshot.expenseCents), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniMetric("IN", money(snapshot.incomeCents), Mint)
                        MiniMetric("OUT", money(snapshot.expenseCents), Color(0xFFFFC5B8))
                        MiniMetric("FREE", money(snapshot.unallocatedCents), Color.White)
                    }
                }
            }
        }
        item {
            SectionHeader("Monthly plan", "Edit", onPlan)
        }
        val fundedRows = snapshot.categoryRows.filter { it.budget != null || it.spentCents > 0 }
        if (fundedRows.isEmpty()) {
            item { EmptyCard("No budget yet", "Fund a category to see your month take shape.", "Start planning", onPlan) }
        } else {
            items(fundedRows.take(5), key = { it.category.id }) { row -> BudgetRowCard(row) }
        }
        item { SectionHeader("Goals") }
        if (snapshot.goalRows.isEmpty()) {
            item { EmptyCard("No active goals", "Create a goal from Plan when you are ready.") }
        } else {
            items(snapshot.goalRows, key = { it.goal.id }) { row ->
                GoalCard(row, onClick = { onGoal(row.goal) })
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun CashFlowSheet(
    data: BudgetData,
    endMonth: YearMonth,
    onDismiss: () -> Unit,
) {
    val points = remember(data.transactions, endMonth) {
        (5 downTo 0).map { offset ->
            val month = endMonth.minusMonths(offset.toLong())
            val start = month.atDay(1).toEpochDay()
            val end = month.plusMonths(1).atDay(1).toEpochDay()
            val transactions = data.transactions.filter { it.dateEpochDay in start until end }
            CashFlowPoint(
                month = month,
                incomeCents = transactions.filter { it.type == "INCOME" }.sumOf { it.amountCents },
                expenseCents = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountCents },
            )
        }
    }
    val latest = points.last()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Cash flow", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CashFlowLegend("Income", money(latest.incomeCents), Forest, Modifier.weight(1f))
                CashFlowLegend("Spending", money(latest.expenseCents), Color(0xFFBF616A), Modifier.weight(1f))
            }
            CashFlowChart(points)
        }
    }
}

@Composable
private fun CashFlowLegend(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CashFlowChart(points: List<CashFlowPoint>) {
    val incomeColor = Forest
    val expenseColor = Color(0xFFBF616A)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(1L, points.maxOfOrNull { max(it.incomeCents, it.expenseCents) } ?: 1L)
    val hasData = points.any { it.incomeCents > 0 || it.expenseCents > 0 }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val baseline = size.height - 2.dp.toPx()
                    val segmentWidth = size.width / points.size
                    val barWidth = segmentWidth * .22f
                    repeat(3) { index ->
                        val y = baseline * index / 2f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    points.forEachIndexed { index, point ->
                        val centerX = segmentWidth * (index + .5f)
                        val incomeHeight = baseline * point.incomeCents / maxValue.toFloat()
                        val expenseHeight = baseline * point.expenseCents / maxValue.toFloat()
                        if (incomeHeight > 0f) {
                            drawRoundRect(
                                color = incomeColor,
                                topLeft = Offset(centerX - barWidth - 2.dp.toPx(), baseline - incomeHeight),
                                size = Size(barWidth, incomeHeight),
                                cornerRadius = CornerRadius(5.dp.toPx()),
                            )
                        }
                        if (expenseHeight > 0f) {
                            drawRoundRect(
                                color = expenseColor,
                                topLeft = Offset(centerX + 2.dp.toPx(), baseline - expenseHeight),
                                size = Size(barWidth, expenseHeight),
                                cornerRadius = CornerRadius(5.dp.toPx()),
                            )
                        }
                    }
                }
                if (!hasData) {
                    Text("No cash flow recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    Text(
                        point.month.format(DateTimeFormatter.ofPattern("MMM")),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, accent: Color) {
    Column {
        Text(label, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelLarge)
        Text(value, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun BudgetRowCard(row: CategoryBudgetRow, onClick: (() -> Unit)? = null) {
    val progress by animateFloatAsState(row.progress, label = "budget progress")
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ColorDot(row.category.colorKey)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.category.name, style = MaterialTheme.typography.titleMedium)
                    Text(row.status, color = if (row.status == "Over budget") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(row.remainingCents), fontWeight = FontWeight.SemiBold)
                    Text("left", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = categoryColor(row.category.colorKey),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${money(row.spentCents)} spent", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (row.budget?.desiredCapCents != null) {
                        "${money(row.spendingLimitCents)} limit"
                    } else {
                        "${money(row.fundedCents)} funded"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoalCard(row: GoalRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = categoryColor(row.goal.colorKey).copy(alpha = .16f)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(row.goal.name, style = MaterialTheme.typography.titleMedium)
                    Text("${(row.progress * 100).toInt()}% complete", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(row.balanceCents), style = MaterialTheme.typography.titleLarge)
            }
            LinearProgressIndicator(
                progress = { row.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = categoryColor(row.goal.colorKey),
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .75f),
            )
            Text("${money((row.goal.targetCents - row.balanceCents).coerceAtLeast(0))} to go", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action) }
            }
        }
    }
}

@Composable
private fun PlanScreen(
    modifier: Modifier,
    month: YearMonth,
    snapshot: MonthSnapshot,
    data: BudgetData,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditBudget: (HedgedDatabase.CategoryEntity) -> Unit,
    onCopyPrevious: () -> Unit,
    onAddGoal: () -> Unit,
    onEditGoal: (HedgedDatabase.GoalEntity) -> Unit,
    onFundGoal: (HedgedDatabase.GoalEntity) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AppHeader("Plan") }
        item { MonthSwitcher(month, onPreviousMonth, onNextMonth) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryPill("Income", money(snapshot.incomeCents), Modifier.weight(1f))
                SummaryPill("Allocated", money(snapshot.allocatedCents), Modifier.weight(1f))
                SummaryPill("Free", money(snapshot.unallocatedCents), Modifier.weight(1f), snapshot.unallocatedCents < 0)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Spending categories", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onCopyPrevious) { Text("Copy last month") }
            }
        }
        items(snapshot.categoryRows, key = { it.category.id }) { row ->
            BudgetRowCard(row, onClick = { onEditBudget(row.category) })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Goals", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onAddGoal) { Text("+ New goal") }
            }
        }
        if (snapshot.goalRows.isEmpty()) {
            item { EmptyCard("Make room for a goal", "Save for something without mixing it into monthly spending.", "Create goal", onAddGoal) }
        } else {
            items(snapshot.goalRows, key = { it.goal.id }) { row ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(row.goal.colorKey)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.goal.name, style = MaterialTheme.typography.titleMedium)
                                Text("${money(row.balanceCents)} of ${money(row.goal.targetCents)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onEditGoal(row.goal) }) { Text("Edit") }
                        }
                        LinearProgressIndicator(
                            progress = { row.progress },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                            color = categoryColor(row.goal.colorKey),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        FilledTonalButton(onClick = { onFundGoal(row.goal) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add or withdraw money")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActivityScreen(
    modifier: Modifier,
    month: YearMonth,
    snapshot: MonthSnapshot,
    data: BudgetData,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEdit: (HedgedDatabase.TransactionEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HedgedDatabase.TransactionEntity?>(null) }
    val categories = data.categories.associateBy { it.id }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AppHeader("Activity") }
        item { MonthSwitcher(month, onPreviousMonth, onNextMonth) }
        if (snapshot.transactions.isEmpty()) {
            item { EmptyCard("Nothing here yet", "Use the + button to add your first transaction.") }
        } else {
            val grouped = snapshot.transactions.groupBy { it.dateEpochDay }
            grouped.forEach { (epochDay, transactions) ->
                item(key = "date-$epochDay") {
                    Text(
                        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(transactions, key = { it.id }) { transaction ->
                    val category = categories[transaction.categoryId]
                    TransactionCard(
                        transaction,
                        category,
                        onEdit = { onEdit(transaction) },
                        onDelete = { pendingDelete = transaction },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    pendingDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("This removes ${money(transaction.amountCents)} from your history.") },
            confirmButton = {
                TextButton(onClick = { onDelete(transaction.id); pendingDelete = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TransactionCard(
    transaction: HedgedDatabase.TransactionEntity,
    category: HedgedDatabase.CategoryEntity?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(categoryColor(category?.colorKey ?: "sage").copy(alpha = .22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (transaction.type == "INCOME") "+" else "−", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(transaction.description?.takeIf { it.isNotBlank() } ?: category?.name ?: "Transaction", style = MaterialTheme.typography.titleMedium)
                Text(category?.name ?: "Archived category", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (transaction.type == "INCOME") "+" else "−") + money(transaction.amountCents),
                    fontWeight = FontWeight.SemiBold,
                    color = if (transaction.type == "INCOME") Forest else MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) { Text("Delete", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    data: BudgetData,
    viewModel: MainViewModel,
    onAddCategory: () -> Unit,
    onEditCategory: (HedgedDatabase.CategoryEntity) -> Unit,
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(context.contentResolver, it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreBackup(context.contentResolver, it) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { AppHeader("Settings") }
        item { SectionHeader("Categories", "+ New", onAddCategory) }
        items(data.categories, key = { it.id }) { category ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onEditCategory(category) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(category.colorKey)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium)
                        Text(category.kind.lowercase().replaceFirstChar { it.titlecase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = category.isActive,
                        onCheckedChange = { viewModel.setCategoryActive(category.id, it) },
                    )
                }
            }
        }
        item { SectionHeader("Backup") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Export a Hedged backup or restore one later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { exportLauncher.launch("Hedged-backup.json") }) { Text("Export") }
                        OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json")) }) { Text("Restore") }
                    }
                }
            }
        }
        item {
            Text(
                "Hedged 1.0",
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TransactionSheet(
    existing: HedgedDatabase.TransactionEntity?,
    categories: List<HedgedDatabase.CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Long?, HedgedDatabase.CategoryEntity, Long, LocalDate, String?, String?) -> Unit,
) {
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: "EXPENSE") }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.amountCents?.let(::editableMoney).orEmpty()) }
    var selectedId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var dateEpochDay by rememberSaveable(existing?.id) { mutableStateOf(existing?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showMore by rememberSaveable(existing?.id) {
        mutableStateOf(!existing?.description.isNullOrBlank() || !existing?.notes.isNullOrBlank())
    }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    val filtered = categories.filter { it.kind == type }
    val selected = filtered.firstOrNull { it.id == selectedId }
    val amountCents = amount.toCentsOrNull()
    val transactionDate = LocalDate.ofEpochDay(dateEpochDay)

    LaunchedEffect(type) {
        if (filtered.none { it.id == selectedId }) selectedId = filtered.firstOrNull()?.id
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (existing == null) "Add transaction" else "Edit transaction", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Expense") })
                FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Income") })
            }
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(transactionDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.moneyInput() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                prefix = { Text("$") },
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            if (filtered.isEmpty()) {
                Text("Create an active ${type.lowercase()} category in Settings first.", color = MaterialTheme.colorScheme.error)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == selectedId,
                            onClick = { selectedId = category.id },
                            label = { Text(category.name) },
                            leadingIcon = { ColorDot(category.colorKey, 10) },
                        )
                    }
                }
            }
            TextButton(onClick = { showMore = !showMore }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showMore) "Fewer details" else "Add details")
            }
            AnimatedVisibility(showMore) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
            Button(
                onClick = { selected?.let { onSave(existing?.id, it, amountCents ?: 0, transactionDate, description, notes) } },
                enabled = selected != null && amountCents != null && amountCents > 0,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (existing == null) "Save ${type.lowercase()}" else "Update transaction") }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = transactionDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dateEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        showDatePicker = false
                    },
                ) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CategorySheet(
    existing: HedgedDatabase.CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "EXPENSE") }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorKey ?: "sage") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(if (existing == null) "New category" else "Edit category", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Text("Type", style = MaterialTheme.typography.labelLarge)
            if (existing == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "EXPENSE", onClick = { kind = "EXPENSE" }, label = { Text("Spending") })
                    FilterChip(selected = kind == "INCOME", onClick = { kind = "INCOME" }, label = { Text("Income") })
                }
            } else {
                Text(
                    if (kind == "EXPENSE") "Spending" else "Income",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ColorPicker(color, onSelected = { color = it })
            Button(
                onClick = { onSave(existing?.id, name, kind, color) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Save category") }
        }
    }
}

@Composable
private fun BudgetSheet(
    category: HedgedDatabase.CategoryEntity,
    existing: HedgedDatabase.BudgetEntity?,
    incomeCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long?, Long, String, Int?) -> Unit,
) {
    var percentageRule by remember(existing?.id) { mutableStateOf(existing?.ruleType == "PERCENT") }
    var cap by remember(existing?.id) { mutableStateOf(existing?.desiredCapCents?.let(::editableMoney).orEmpty()) }
    var funded by remember(existing?.id) { mutableStateOf(editableMoney(existing?.fundedCents ?: 0)) }
    var percent by remember(existing?.id) { mutableStateOf(((existing?.rateBasisPoints ?: 1000) / 100.0).stripTrailingZeroes()) }
    val rate = percent.toBigDecimalOrNull()?.multiply(BigDecimal(100))?.setScale(0, RoundingMode.HALF_UP)?.toInt()
    val calculated = if (rate != null) incomeCents * rate / 10_000 else 0
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(category.colorKey)
                Spacer(Modifier.width(10.dp))
                Text(category.name, style = MaterialTheme.typography.headlineMedium)
            }
            Text("Monthly funding", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Percentage of income", style = MaterialTheme.typography.titleMedium)
                    Text("Useful for giving or flexible rules", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = percentageRule, onCheckedChange = { percentageRule = it })
            }
            if (percentageRule) {
                OutlinedTextField(
                    value = percent,
                    onValueChange = { percent = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                    label = { Text("Percent of income") },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                Text("This month: ${money(calculated)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(
                    value = funded,
                    onValueChange = { funded = it.moneyInput() },
                    label = { Text("Funded amount") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            OutlinedTextField(
                value = cap,
                onValueChange = { cap = it.moneyInput() },
                label = { Text("Spending limit (optional)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                onClick = {
                    onSave(
                        cap.toCentsOrNull()?.takeIf { it > 0 },
                        if (percentageRule) 0 else funded.toCentsOrNull() ?: 0,
                        if (percentageRule) "PERCENT" else "FIXED",
                        if (percentageRule) rate else null,
                    )
                },
                enabled = !percentageRule || (rate != null && rate in 0..10000),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Save budget") }
        }
    }
}

@Composable
private fun GoalSheet(
    existing: HedgedDatabase.GoalEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, Long, String) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var target by remember(existing?.id) { mutableStateOf(existing?.targetCents?.let(::editableMoney).orEmpty()) }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorKey ?: "mint") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(if (existing == null) "New goal" else "Edit goal", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Goal name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.moneyInput() },
                label = { Text("Target") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            ColorPicker(color, onSelected = { color = it })
            Button(
                onClick = { onSave(existing?.id, name, target.toCentsOrNull() ?: 0, color) },
                enabled = name.trim().isNotEmpty() && (target.toCentsOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Save goal") }
        }
    }
}

@Composable
private fun GoalEntrySheet(
    goal: HedgedDatabase.GoalEntity,
    balanceCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long, String?) -> Unit,
) {
    var isContribution by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val cents = amount.toCentsOrNull()
    val withdrawalTooLarge = !isContribution && cents != null && cents > balanceCents
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn {
            Text(goal.name, style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = isContribution, onClick = { isContribution = true }, label = { Text("Contribution") })
                FilterChip(selected = !isContribution, onClick = { isContribution = false }, label = { Text("Withdrawal") })
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.moneyInput() },
                label = { Text("Amount") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            if (!isContribution) {
                Text(
                    if (withdrawalTooLarge) "Withdrawal exceeds ${money(balanceCents)}" else "Available: ${money(balanceCents)}",
                    color = if (withdrawalTooLarge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Button(
                onClick = { onSave(if (isContribution) cents ?: 0 else -(cents ?: 0), note) },
                enabled = cents != null && cents > 0 && !withdrawalTooLarge,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (isContribution) "Add contribution" else "Record withdrawal") }
        }
    }
}

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun ColorPicker(selected: String, onSelected: (String) -> Unit) {
    Text("Color", style = MaterialTheme.typography.labelLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(listOf("mint", "sage", "amber", "blue", "violet", "coral")) { key ->
            Box(
                modifier = Modifier
                    .size(if (selected == key) 42.dp else 36.dp)
                    .clip(CircleShape)
                    .background(categoryColor(key))
                    .clickable { onSelected(key) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == key) Text("✓", color = Evergreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ColorDot(key: String, size: Int = 14) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(categoryColor(key)))
}

private fun categoryColor(key: String): Color = when (key) {
    "mint" -> Color(0xFFA3BE8C)
    "sage" -> Color(0xFF8FBCBB)
    "amber" -> Color(0xFFEBCB8B)
    "blue" -> Color(0xFF5E81AC)
    "violet" -> Color(0xFFB48EAD)
    "coral" -> Color(0xFFBF616A)
    else -> Color(0xFF8FBCBB)
}

private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

private fun money(cents: Long): String = currencyFormatter.format(BigDecimal(cents).movePointLeft(2))

private fun editableMoney(cents: Long): String = BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun String.moneyInput(): String = filter { it.isDigit() || it == '.' }.take(12).let { raw ->
    val dot = raw.indexOf('.')
    if (dot < 0) raw else raw.take(dot + 3)
}

private fun String.toCentsOrNull(): Long? = toBigDecimalOrNull()
    ?.multiply(BigDecimal(100))
    ?.setScale(0, RoundingMode.HALF_UP)
    ?.longValueExact()

private fun Double.stripTrailingZeroes(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
