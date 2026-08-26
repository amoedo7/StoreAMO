package com.desarrollamo.storeamo.update

import com.desarrollamo.storeamo.model.StoreApp
import com.desarrollamo.storeamo.model.StoreArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateEnvironmentTest {
    @Test
    fun networkClassificationFailsClosed() {
        assertEquals(NetworkKind.OFFLINE, UpdateEnvironment.classifyNetwork(hasInternet = false, isWifi = true))
        assertEquals(NetworkKind.WIFI, UpdateEnvironment.classifyNetwork(hasInternet = true, isWifi = true))
        assertEquals(NetworkKind.METERED, UpdateEnvironment.classifyNetwork(hasInternet = true, isWifi = false))
    }

    @Test
    fun validVerifiedArtifactBecomesDownloadableCandidate() {
        val binding = UpdateCatalogAdapter.bind(app(), artifact(), installedVersionCode = 4L)
        assertEquals("demo", binding.candidate.appId)
        assertEquals(4L, binding.candidate.installedVersionCode)
        assertEquals(5L, binding.candidate.targetVersionCode)
        assertTrue(binding.candidate.verified)
        assertTrue(binding.candidate.downloadable)
    }

    @Test
    fun unsafeOrMalformedCatalogMetadataFailsClosed() {
        val badUrl = UpdateCatalogAdapter.bind(app(), artifact(url = "http://example.com/app.apk"), 4L)
        val badHash = UpdateCatalogAdapter.bind(app(), artifact(sha256 = "bad"), 4L)
        val badCode = UpdateCatalogAdapter.bind(app(), artifact(versionCode = "nope"), 4L)
        val missingPackage = UpdateCatalogAdapter.bind(app(), artifact(applicationId = null), 4L)

        assertFalse(badUrl.candidate.downloadable)
        assertFalse(badHash.candidate.downloadable)
        assertFalse(badCode.candidate.downloadable)
        assertFalse(missingPackage.candidate.downloadable)
    }

    @Test
    fun candidateChannelPreservesCatalogVerificationState() {
        val binding = UpdateCatalogAdapter.bind(app(), artifact(verified = false), installedVersionCode = 4L)
        assertFalse(binding.candidate.verified)
        assertTrue(binding.candidate.downloadable)
    }

    private fun app() = StoreApp(
        id = "demo",
        name = "DemoAMO",
        tagline = "demo",
        description = "demo",
        category = "tools",
        featured = false,
        audience = "public",
        status = "active",
        supportedPlatforms = listOf("android"),
        repository = null,
        artifacts = emptyList(),
    )

    private fun artifact(
        versionCode: String = "5",
        url: String = "https://example.com/app.apk",
        sha256: String = "a".repeat(64),
        applicationId: String? = "com.desarrollamo.demo",
        verified: Boolean = true,
    ) = StoreArtifact(
        platform = "android",
        arch = null,
        format = "apk",
        version = "0.1.0",
        versionCode = versionCode,
        url = url,
        sha256 = sha256,
        sizeBytes = 100L,
        verified = verified,
        applicationId = applicationId,
        verificationReport = null,
    )
}
