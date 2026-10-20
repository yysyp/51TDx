package com.ran.app.ui

import com.ran.app.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ran.app.ui.components.FeedbackBanner
import com.ran.app.ui.components.StarBadge
import com.ran.app.ui.screens.FoodScreen
import com.ran.app.ui.screens.HomeScreen
import com.ran.app.ui.screens.LotteryScreen
import com.ran.app.ui.screens.PetScreen
import com.ran.app.ui.screens.PlansScreen
import com.ran.app.ui.screens.ReviewScreen
import com.ran.app.ui.screens.TodosScreen

private enum class RanTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    REVIEW("复习", Icons.Default.Refresh),
    PLANS("计划", Icons.Default.CheckCircle),
    LOTTERY("抽奖", Icons.Default.Casino),
    PET("宠物", Icons.Default.Pets),
    FOOD("吃什么", Icons.Default.Restaurant)
}

@Composable
fun RanApp() {
    val vm: RanViewModel = viewModel()
    var currentTab by rememberSaveable { mutableStateOf(RanTab.HOME) }
    var plansSubTab by rememberSaveable { mutableStateOf(0) }

    val stats by vm.homeStats.collectAsStateWithLifecycle()
    val prizes by vm.prizes.collectAsStateWithLifecycle()
    val drawRecords by vm.drawRecords.collectAsStateWithLifecycle()
    val activePlans by vm.activePlans.collectAsStateWithLifecycle()
    val completedPlans by vm.completedPlans.collectAsStateWithLifecycle()
    val openTodos by vm.openTodos.collectAsStateWithLifecycle()
    val completedTodos by vm.completedTodos.collectAsStateWithLifecycle()
    val foods by vm.foods.collectAsStateWithLifecycle()
    val pet by vm.pet.collectAsStateWithLifecycle()
    val dueReviews by vm.dueReviews.collectAsStateWithLifecycle()
    val reviewItems by vm.reviewItems.collectAsStateWithLifecycle()
    val reviewSummary by vm.reviewSummary.collectAsStateWithLifecycle()
    val drawState by vm.drawState.collectAsStateWithLifecycle()
    val foodPick by vm.foodPick.collectAsStateWithLifecycle()
    val foodRolling by vm.foodRolling.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()

    // Plans checked in today, used to disable repeat check-in buttons.
    // Collected rather than computed once: the underlying rows never change at
    // midnight, so only a live day key can re-arm tomorrow's check-in.
    val checkedInPlanIds by vm.checkedInPlanIdsToday.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(stats.stars, currentTab)
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                RanTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                RanTab.HOME -> HomeScreen(
                    stats = stats,
                    activePlanTitles = activePlans.map { it.title },
                    onResetStars = vm::resetStarBalance
                )
                RanTab.REVIEW -> ReviewScreen(
                    dueItems = dueReviews,
                    allItems = reviewItems,
                    summary = reviewSummary,
                    onAdd = vm::addReviewItem,
                    onGrade = vm::gradeReview,
                    onDelete = vm::deleteReviewItem
                )
                RanTab.PLANS -> Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    SubTabBar(
                        options = listOf("任务计划" to Icons.Default.CheckCircle, "待办清单" to Icons.AutoMirrored.Filled.FactCheck),
                        selectedIndex = plansSubTab,
                        onSelect = { plansSubTab = it }
                    )
                    if (plansSubTab == 0) {
                        PlansScreen(
                            activePlans = activePlans,
                            completedPlans = completedPlans,
                            checkedInPlanIds = checkedInPlanIds,
                            onCheckIn = vm::checkIn,
                            onSave = vm::savePlan,
                            onDelete = vm::deletePlan,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        TodosScreen(
                            openTodos = openTodos,
                            completedTodos = completedTodos,
                            onToggle = vm::toggleTodo,
                            onSave = vm::saveTodo,
                            onDelete = vm::deleteTodo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                RanTab.LOTTERY -> LotteryScreen(
                    prizes = prizes,
                    drawState = drawState,
                    records = drawRecords,
                    starBalance = stats.stars,
                    onStartRoll = vm::startRoll,
                    onStopRoll = vm::stopRoll,
                    onScratched = vm::finishScratch,
                    onScratchReminder = vm::remindToScratch,
                    onClearResult = vm::clearDrawResult,
                    onSave = vm::savePrize,
                    onDelete = vm::deletePrize
                )
                RanTab.PET -> PetScreen(
                    pet = pet,
                    stars = stats.stars,
                    onAction = vm::petAction,
                    onBuy = vm::buyAccessory,
                    onRename = vm::renamePet,
                    onMoveAccessory = vm::moveAccessory,
                    onResetAccessory = vm::resetAccessory,
                    onResetAllAccessories = vm::resetAllAccessories
                )
                RanTab.FOOD -> FoodScreen(
                    foods = foods,
                    pickedFood = foodPick,
                    isRolling = foodRolling,
                    onPick = vm::pickFood,
                    onClearPick = vm::clearFoodPick,
                    onSave = vm::saveFood,
                    onDelete = vm::deleteFood
                )
            }

            AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                FeedbackBanner(feedback = feedback, onDismiss = vm::consumeFeedback)
            }
        }
    }
}

/** Horizontal segmented control used to switch plans / todos. */
@Composable
private fun SubTabBar(
    options: List<Pair<String, ImageVector>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (label, icon) ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TopBar(stars: Int, tab: RanTab) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = tabTitle(tab),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StarBadge(stars = stars)
        }
    }
}

private fun tabTitle(tab: RanTab): String = when (tab) {
    RanTab.HOME -> "今天也要好好加油"
    RanTab.REVIEW -> "在忘记之前再见一面"
    RanTab.PLANS -> "计划与打卡"
    RanTab.LOTTERY -> "完成目标，换取奖励"
    RanTab.PET -> "你赚的星星，它会记得"
    RanTab.FOOD -> "选择困难症救星"
}