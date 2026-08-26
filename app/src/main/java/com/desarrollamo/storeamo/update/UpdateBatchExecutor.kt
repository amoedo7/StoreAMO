package com.desarrollamo.storeamo.update

enum class UpdateBatchItemStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

data class UpdateBatchExecutionItem(
    val appId: String,
    val status: UpdateBatchItemStatus = UpdateBatchItemStatus.PENDING,
    val lastError: String? = null,
)

data class UpdateBatchExecutionState(
    val items: List<UpdateBatchExecutionItem>,
    val activeAppId: String? = null,
) {
    val isFinished: Boolean
        get() = activeAppId == null && items.all { it.status == UpdateBatchItemStatus.SUCCEEDED || it.status == UpdateBatchItemStatus.FAILED }
}

sealed interface UpdateBatchExecutionAction {
    data class Start(val appId: String) : UpdateBatchExecutionAction
    data class Reconcile(val appId: String) : UpdateBatchExecutionAction
    data object Complete : UpdateBatchExecutionAction
}

/**
 * Pure sequential executor for update batches.
 *
 * This class owns only ordering/recovery semantics. Android installation stays in
 * DownloadInstaller / PackageInstaller and is invoked by the UI/controller when
 * [nextAction] returns [UpdateBatchExecutionAction.Start].
 */
object UpdateBatchExecutor {
    fun begin(plan: UpdateBatchPlan): UpdateBatchExecutionState {
        val seen = mutableSetOf<String>()
        val items = plan.eligible.map { candidate ->
            require(candidate.appId.isNotBlank()) { "appId must not be blank" }
            require(seen.add(candidate.appId)) { "duplicate eligible appId: ${candidate.appId}" }
            UpdateBatchExecutionItem(appId = candidate.appId)
        }
        return UpdateBatchExecutionState(items = items)
    }

    fun nextAction(state: UpdateBatchExecutionState): UpdateBatchExecutionAction {
        val active = state.activeAppId
        if (active != null) return UpdateBatchExecutionAction.Reconcile(active)

        val next = state.items.firstOrNull { it.status == UpdateBatchItemStatus.PENDING }
            ?: return UpdateBatchExecutionAction.Complete
        return UpdateBatchExecutionAction.Start(next.appId)
    }

    fun markStarted(state: UpdateBatchExecutionState, appId: String): UpdateBatchExecutionState {
        require(state.activeAppId == null) { "another update is already active: ${state.activeAppId}" }
        val item = state.items.firstOrNull { it.appId == appId }
            ?: error("unknown appId: $appId")
        require(item.status == UpdateBatchItemStatus.PENDING) { "app is not pending: $appId" }

        return state.copy(
            items = state.items.map {
                if (it.appId == appId) it.copy(status = UpdateBatchItemStatus.RUNNING, lastError = null) else it
            },
            activeAppId = appId,
        )
    }

    fun recordResult(
        state: UpdateBatchExecutionState,
        appId: String,
        success: Boolean,
        error: String? = null,
    ): UpdateBatchExecutionState {
        require(state.activeAppId == appId) { "result does not match active app: $appId" }
        val terminalStatus = if (success) UpdateBatchItemStatus.SUCCEEDED else UpdateBatchItemStatus.FAILED
        return state.copy(
            items = state.items.map {
                if (it.appId == appId) it.copy(status = terminalStatus, lastError = if (success) null else (error ?: "INSTALL_FAILED")) else it
            },
            activeAppId = null,
        )
    }

    /**
     * Recovery path after process/activity recreation.
     * Never retries an in-flight installation blindly. The caller must first
     * reconcile Android's installed state, then report the result explicitly.
     */
    fun reconcile(
        state: UpdateBatchExecutionState,
        appId: String,
        targetInstalled: Boolean,
        error: String? = null,
    ): UpdateBatchExecutionState {
        require(state.activeAppId == appId) { "reconcile does not match active app: $appId" }
        return recordResult(
            state = state,
            appId = appId,
            success = targetInstalled,
            error = if (targetInstalled) null else (error ?: "RECONCILE_NOT_INSTALLED"),
        )
    }
}
