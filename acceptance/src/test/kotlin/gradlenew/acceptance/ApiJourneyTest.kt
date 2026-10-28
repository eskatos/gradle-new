package gradlenew.acceptance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiJourneyTest {

    @Test
    fun apiJourney() {
        Target.open().use { target ->
            val versions = target.get("/api/versions")
            assertEquals(200, versions.statusCode())
            assertEquals("application/json", versions.headers().firstValue("content-type").orElse("").substringBefore(';'))
            assertEquals(expectedSiteFile("versions.json"), versions.body().trim())
        }
    }

    private fun expectedSiteFile(name: String): String {
        val siteDir = System.getProperty("acceptance.siteDir") ?: error("acceptance.siteDir is not set: no site was provided")
        return File(siteDir, name).readText().trim()
    }
}
