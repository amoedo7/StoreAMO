package com.desarrollamo.storeamo.update

import com.desarrollamo.storeamo.model.StoreApp
import com.desarrollamo.storeamo.model.StoreArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBatchArtifactBridgeTest {
    @Test
    fun `start action returns exact admitted artifact`() {
        val first = binding("alpha", installed = 1, target = 2)
        val second = binding("beta", installed = 3, target = 4)
        val batch = UpdateBatchArtifactBridge.begin(
            bindings = listOf(second, first),
            preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.WIFI,
        )

        val action = UpdateBatchArtifactBridge.nextAction(batch) as BoundUpdateAction.Start
        assertEquals("alpha", action.request.appId)
        assertEquals(first.artifact, action.request.artifact)
        assertEquals(2L, action.request.targetVersionCode)
    }

    @Test
    fun `tampered install request is rejected before start`() {
        val admitted = binding("alpha", installed = 1, target = 2)
        val batch = UpdateBatchArtifactBridge.begin(
            bindings = listOf(admitted),
            preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.WIFI,
        )
        val action = UpdateBatchArtifactBridge.nextAction(batch) as BoundUpdateAction.Start
        val tampered = action.request.copy(
            artifact = action.request.artifact.copy(url = "https://example.invalid/other.apk"),
        )

        val failed = runCatching { UpdateBatchArtifactBridge.markStarted(batch, tampered) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `reconcile keeps admitted package and persisted target`() {
        val admitted = binding("alpha", installed = 1, target = 7)
        var batch = UpdateBatchArtifactBridge.begin(
            bindings = listOf(admitted),
            preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
            trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
            network = NetworkKind.WIFI,
        )
        val start = UpdateBatchArtifactBridge.nextAction(batch) as BoundUpdateAction.Start
        batch = UpdateBatchArtifactBridge.markStarted(batch, start.request)

        val reconcile = UpdateBatchArtifactBridge.nextAction(batch) as BoundUpdateAction.Reconcile
        assertEquals("com.desarrollamo.alpha", reconcile.applicationId)
        assertEquals(7L, reconcile.targetVersionCode)

        batch = UpdateBatchArtifactBridge.reconcileActive(batch, installedVersionCode = 7)
        assertTrue(UpdateBatchCoordinator.summary(batch.coordinator).finished)
        assertEquals(1, UpdateBatchCoordinator.summary(batch.coordinator).succeeded)
    }

    @Test
    fun `candidate artifact mismatch fails closed`() {
        val valid = binding("alpha", installed = 1, target = 2)
        val invalid = valid.copy(
            candidate = valid.candidate.copy(targetVersionCode = 3),
        )

        val failed = runCatching {
            UpdateBatchArtifactBridge.begin(
                bindings = listOf(invalid),
                preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
                trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
                network = NetworkKind.WIFI,
            )
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `duplicate artifact binding fails closed`() {
        val first = binding("alpha", installed = 1, target = 2)
        val duplicate = binding("alpha", installed = 1, target = 3)

        val failed = runCatching {
            UpdateBatchArtifactBridge.begin(
                bindings = listOf(first, duplicate),
                preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
                trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
                network = NetworkKind.WIFI,
            )
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `non android artifact binding fails closed`() {
        val valid = binding("alpha", installed = 1, target = 2)
        val invalid = valid.copy(
            artifact = valid.artifact.copy(platform = "web"),
        )

        val failed = runCatching {
            UpdateBatchArtifactBridge.begin(
                bindings = listOf(invalid),
                preferences = UpdatePolicyPreferences(channel = UpdateChannel.STABLE, wifiOnly = false),
                trigger = UpdateTrigger.MANUAL_UPDATE_ALL,
                network = NetworkKind.WIFI,
            )
        }
        assertTrue(failed.isFailure)
    }

    private fun binding(id: String, installed: Long, target: Long): UpdateCatalogAdapter.CandidateBinding {
        val artifact = StoreArtifact(
            platform = "android",
            arch = null,
            format = "apk",
            version = "0.0.$target",
            versionCode = target.toString(),
            url = "https://example.com/$id-$target.apk",
            sha256 = "a".repeat(64),
            sizeBytes = 1234,
            verified = true,
            applicationId = "com.desarrollamo.$id",
            verificationReport = null,
        )
        val app = StoreApp(
            id = id,
            name = id.replaceFirstChar { it.uppercase() },
            tagline = "test",
            description = "test",
            category = "test",
            featured = false,
            audience = "test",
            status = "available",
            supportedPlatforms = listOf("android"),
            repository = null,
            artifacts = listOf(artifact),
        )
        return UpdateCatalogAdapter.CandidateBinding(
            app = app,
            artifact = artifact,
            candidate = UpdateCandidate(
                appId = id,
                installedVersionCode = installed,
                targetVersionCode = target,
                verified = true,
                downloadable = true,
            ),
        )
    }
}
