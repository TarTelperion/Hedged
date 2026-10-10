package com.hedged.app

import android.app.Application
import com.hedged.app.data.BudgetRepository
import com.hedged.app.data.HedgedDatabase

class HedgedApplication : Application() {
    val repository: BudgetRepository by lazy {
        BudgetRepository(HedgedDatabase.getInstance(this))
    }
}
