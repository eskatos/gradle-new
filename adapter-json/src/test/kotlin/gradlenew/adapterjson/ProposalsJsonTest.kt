package gradlenew.adapterjson

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
import kotlin.test.Test
import kotlin.test.assertEquals

class ProposalsJsonTest {

    private val stable990 = Checksums("1".repeat(64), "2".repeat(64))
    private val stable982 = Checksums("3".repeat(64), "4".repeat(64))
    private val rc = Checksums("5".repeat(64), "6".repeat(64))

    private val proposals = listOf(
        Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null, stable990)),
        Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin("9.8.2", "9.10.0-rc-1", stable982, rc)),
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
                            "rc": null,
                            "checksums": {
                                "stable": {
                                    "distribution": "1111111111111111111111111111111111111111111111111111111111111111",
                                    "wrapper": "2222222222222222222222222222222222222222222222222222222222222222"
                                },
                                "rc": null
                            }
                        }
                    },
                    {
                        "slot": "rc",
                        "branch": "offer-gradle/rc",
                        "title": "Offer Gradle 9.10.0-rc-1",
                        "pin": {
                            "stable": "9.8.2",
                            "rc": "9.10.0-rc-1",
                            "checksums": {
                                "stable": {
                                    "distribution": "3333333333333333333333333333333333333333333333333333333333333333",
                                    "wrapper": "4444444444444444444444444444444444444444444444444444444444444444"
                                },
                                "rc": {
                                    "distribution": "5555555555555555555555555555555555555555555555555555555555555555",
                                    "wrapper": "6666666666666666666666666666666666666666666666666666666666666666"
                                }
                            }
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
        val unpinned = listOf(Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin("9.9.0", null)))
        assertEquals(unpinned, ProposalsJson.decode(ProposalsJson.encode(unpinned)))
    }
}
