package com.desarrollamo.storeamo.update

import com.desarrollamo.storeamo.model.StoreArtifact

/**
 * Binds the pure update coordinator to the exact catalog artifacts admitted at
 * batch creation time.
 *
 * The visible `Actualizar todo` flow must use this bridge instead of looking an
 * app up again in a potentially newer catalog after policy admission. This keeps
 * app identity, target version and artifact metadata stable for the whole batch.
 */
data class BoundUpdateBatch(
    val coordinator: UpdateBatchCoordinatorState,
    val bindings: Map<String, UpdateCatalogAdapter.CandidateBinding>,
)

data class UpdateInstallRequest(
    val appId: String,
    val appName: String,
    val artifact: StoreArtifact,
    val targetVersionCode: Long,
)

sealed interface BoundUpdateAction {
    data class Start(val request: UpdateInstallRequest) : BoundUpdateAction
    data class Reconcile(
        val appId: String,
        val applicationId: String,
        val targetVersionCode: Long,
    ) : BoundUpdateAction
    data object Complete : BoundUpdateAction
}

object UpdateBatchArtifactBridge {
    fun begin(
        bindings: List<UpdateCatalogAdapter.CandidateBinding>,
        preferences: UpdatePolicyPreferences,
        trigger: UpdateTrigger,
        network: NetworkKind,
    ): BoundUpdateBatch {
        val byId = linkedMapOf<String, UpdateCatalogAdapter.CandidateBinding>()
        bindings.forEach { binding ->
            validateBinding(binding)
            require(byId.put(binding.app.id, binding) == null) {
                "duplicate binding appId: ${binding.app.id}"
            }
        }
        val coordinator = UpdateBatchCoordinator.begin(
            candidates = bindings.map { it.candidate },
            preferences = preferences,
            trigger = trigger,
            network = network,
        )
        return BoundUpdateBatch(coordinator = coordinator, bindings = byId)
    }

    fun nextAction(batch: BoundUpdateBatch): BoundUpdateAction = when (val action = UpdateBatchCoordinator.nextAction(batch.coordinator)) {
        is UpdateBatchExecutionAction.Start -> {
            val binding = requireBinding(batch, action.appId)
            BoundUpdateAction.Start(binding.toInstallRequest())
        }
        is UpdateBatchExecutionAction.Reconcile -> {
            val binding = requireBinding(batch, action.appId)
            val applicationId = binding.artifact.applicationId
                ?.takeIf { it.isNotBlank() }
                ?: error("eligible artifact lost applicationId: ${action.appId}")
            val executionItem = batch.coordinator.execution.items.firstOrNull { it.appId == action.appId }
                ?: error("active execution item missing: ${action.appId}")
            BoundUpdateAction.Reconcile(
                appId = action.appId,
                applicationId = applicationId,
                targetVersionCode = executionItem.targetVersionCode,
            )
        }
        UpdateBatchExecutionAction.Complete -> BoundUpdateAction.Complete
    }

    fun markStarted(batch: BoundUpdateBatch, request: UpdateInstallRequest): BoundUpdateBatch {
        val binding = requireBinding(batch, request.appId)
        val expected = binding.toInstallRequest()
        require(request == expected) { "install request no longer matches admitted artifact: ${request.appId}" }
        return batch.copy(coordinator = UpdateBatchCoordinator.markStarted(batch.coordinator, request.appId))
    }

    fun recordInstallResult(
        batch: BoundUpdateBatch,
        appId: String,
        success: Boolean,
        error: String? = null,
    ): BoundUpdateBatch = batch.copy(
        coordinator = UpdateBatchCoordinator.recordInstallResult(
            state = batch.coordinator,
            appId = appId,
            success = success,
            error = error,
        )
    )

    fun reconcileActive(batch: BoundUpdateBatch, installedVersionCode: Long?): BoundUpdateBatch = batch.copy(
        coordinator = UpdateBatchCoordinator.reconcileActive(
            state = batch.coordinator,
            installedVersionCode = installedVersionCode,
        )
    )

    private fun validateBinding(binding: UpdateCatalogAdapter.CandidateBinding) {
        require(binding.app.id.isNotBlank()) { "blank app id" }
        require(binding.candidate.appId == binding.app.id) { "candidate/app id mismatch: ${binding.app.id}" }
        val artifactVersion = binding.artifact.versionCode.toLongOrNull()
        require(artifactVersion != null && artifactVersion == binding.candidate.targetVersionCode) {
            "candidate/artifact target mismatch: ${binding.app.id}"
        }
        require(binding.artifact.platform == "android") { "non-Android artifact in update batch: ${binding.app.id}" }
    }

    private fun requireBinding(batch: BoundUpdateBatch, appId: String): UpdateCatalogAdapter.CandidateBinding =
        batch.bindings[appId] ?: error("missing admitted artifact binding: $appId")

    private fun UpdateCatalogAdapter.CandidateBinding.toInstallRequest(): UpdateInstallRequest = UpdateInstallRequest(
        appId = app.id,
        appName = app.name,
        artifact = artifact,
        targetVersionCode = candidate.targetVersionCode,
    )
}
