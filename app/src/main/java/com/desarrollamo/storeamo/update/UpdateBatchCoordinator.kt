package com.desarrollamo.storeamo.update

/**
 * Single pure entry point for the visible `Actualizar todo` / auto-update flow.
 *
 * UI code must not independently reimplement policy, ordering or recovery rules.
 * This coordinator always creates the execution state from UpdateBatchPolicy and
 * advances it through UpdateBatchExecutor.
 */
data class UpdateBatchCoordinatorState(
    val plan: UpdateBatchPlan,
    val execution: UpdateBatchExecutionState,
)

data class UpdateBatchSummary(
    val eligible: Int,
    val blocked: Int,
    val pending: Int,
    val running: Int,
    val succeeded: Int,
    val failed: Int,
    val finished: Boolean,
)

object UpdateBatchCoordinator {
    fun begin(
        candidates: List<UpdateCandidate>,
        preferences: UpdatePolicyPreferences,
        trigger: UpdateTrigger,
        network: NetworkKind,
    ): UpdateBatchCoordinatorState {
        val plan = UpdateBatchPolicy.plan(
            candidates = candidates,
            preferences = preferences,
            trigger = trigger,
            network = network,
        )
        return UpdateBatchCoordinatorState(
            plan = plan,
            execution = UpdateBatchExecutor.begin(plan),
        )
    }

    fun nextAction(state: UpdateBatchCoordinatorState): UpdateBatchExecutionAction =
        UpdateBatchExecutor.nextAction(state.execution)

    fun markStarted(state: UpdateBatchCoordinatorState, appId: String): UpdateBatchCoordinatorState {
        require(state.plan.eligible.any { it.appId == appId }) { "app is not eligible in this batch: $appId" }
        return state.copy(execution = UpdateBatchExecutor.markStarted(state.execution, appId))
    }

    fun recordInstallResult(
        state: UpdateBatchCoordinatorState,
        appId: String,
        success: Boolean,
        error: String? = null,
    ): UpdateBatchCoordinatorState = state.copy(
        execution = UpdateBatchExecutor.recordResult(
            state = state.execution,
            appId = appId,
            success = success,
            error = error,
        )
    )

    /**
     * Process/activity recreation recovery.
     *
     * Success is inferred only from Android's installed version code meeting the
     * exact target that was admitted by policy. A merely-installed older package
     * never counts as a successful update.
     */
    fun reconcileActive(
        state: UpdateBatchCoordinatorState,
        installedVersionCode: Long?,
    ): UpdateBatchCoordinatorState {
        val activeAppId = state.execution.activeAppId
            ?: error("there is no active update to reconcile")
        val candidate = state.plan.eligible.firstOrNull { it.appId == activeAppId }
            ?: error("active app is missing from eligible plan: $activeAppId")
        val targetInstalled = installedVersionCode != null && installedVersionCode >= candidate.targetVersionCode
        return state.copy(
            execution = UpdateBatchExecutor.reconcile(
                state = state.execution,
                appId = activeAppId,
                targetInstalled = targetInstalled,
                error = if (targetInstalled) null else "TARGET_VERSION_NOT_INSTALLED",
            )
        )
    }

    fun summary(state: UpdateBatchCoordinatorState): UpdateBatchSummary {
        val items = state.execution.items
        return UpdateBatchSummary(
            eligible = state.plan.eligible.size,
            blocked = state.plan.blocked.size,
            pending = items.count { it.status == UpdateBatchItemStatus.PENDING },
            running = items.count { it.status == UpdateBatchItemStatus.RUNNING },
            succeeded = items.count { it.status == UpdateBatchItemStatus.SUCCEEDED },
            failed = items.count { it.status == UpdateBatchItemStatus.FAILED },
            finished = state.execution.isFinished,
        )
    }
}
