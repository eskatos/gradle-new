package gradlenew.adapterjson

import gradlenew.versions.Pin
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
import kotlin.test.Test
import kotlin.test.assertEquals

class ProposalsJsonTest {

    private val proposals = listOf(
        Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null)),
        Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin("9.8.2", "9.10.0-rc-1")),
    )

    @Test
    fun encodesSlotBranchTitleAndTheCompletePin() {
        assertEquals(
            """
            {
                "proposals": [
                    {
                        "slot": "stable",
                        "branch": "offer-gradle/stable",
                        "title": "Offer Gradle 9.9.0",
                        "pin": {
                            "stable": "9.9.0",
                            "rc": null
                        }
                    },
                    {
                        "slot": "rc",
                        "branch": "offer-gradle/rc",
                        "title": "Offer Gradle 9.10.0-rc-1",
                        "pin": {
                            "stable": "9.8.2",
                            "rc": "9.10.0-rc-1"
                        }
                    }
                ]
            }
            """.trimIndent(),
            ProposalsJson.encode(proposals),
        )
    }

    @Test
    fun roundTrips() {
        assertEquals(proposals, ProposalsJson.decode(ProposalsJson.encode(proposals)))
        assertEquals(emptyList(), ProposalsJson.decode(ProposalsJson.encode(emptyList())))
    }
}
