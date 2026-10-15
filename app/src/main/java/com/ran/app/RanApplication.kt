package com.ran.app

import android.app.Application
import com.ran.app.core.RewardEngine
import com.ran.app.data.RanRepository
import com.ran.app.data.local.RanDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RanApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: RanRepository by lazy {
        val db = RanDatabase.get(this)
        RanRepository(
            prizeDao = db.prizeDao(),
            drawRecordDao = db.drawRecordDao(),
            planDao = db.planDao(),
            planCheckInDao = db.planCheckInDao(),
            todoDao = db.todoDao(),
            foodDao = db.foodDao(),
            petDao = db.petDao(),
            reviewDao = db.reviewDao(),
            reviewLogDao = db.reviewLogDao(),
            starLedgerDao = db.starLedgerDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.seedIfEmpty()
            // Created alongside seeding rather than at build time: a fresh pet
            // must exist before the first screen renders, but must never be
            // restored over data the user already has.
            repository.ensurePet()
            // Welcome endowment: a small non-zero starting balance so the user
            // can experience one reward cycle before earning anything (endowed-progress effect).
            if (repository.currentBalance() == 0) {
                repository.awardStars(RewardEngine.DAILY_GOAL_BASE, "新启程礼包")
            }
        }
    }
}