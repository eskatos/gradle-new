package gradlenew.adapterjson

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.PinnedVersion
import gradlenew.versions.Proposal
import gradlenew.versions.Slot
import kotlin.test.Test
import kotlin.test.assertEquals

class ProposalsJsonTest {

    private val stable990 = PinnedVersion("9.9.0", 25, Checksums("1".repeat(64), "2".repeat(64)))
    private val stable982 = PinnedVersion("9.8.2", 25, Checksums("3".repeat(64), "4".repeat(64)))
    private val rc = PinnedVersion("9.10.0-rc-1", 25, Checksums("5".repeat(64), "6".repeat(64)))
    private val previous = PinnedVersion("8.14.6", 21)

    private val proposals = listOf(
        Proposal(Slot.STABLE, "Offer Gradle 9.9.0", Pin(stable990, previous)),
        Proposal(Slot.RC, "Offer Gradle 9.10.0-rc-1", Pin(stable982, previous, rc)),
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
                            "stable": {
                                "version": "9.9.0",
                                "java": 25,
                                "checksums": {
                                    "distribution": "1111111111111111111111111111111111111111111111111111111111111111",
                                    "wrapper": "2222222222222222222222222222222222222222222222222222222222222222"
                                }
                            },
                            "rc": null,
                            "previous": {
                                "version": "8.14.6",
                                "java": 21,
                                "checksums": null
                            }
                        }
                    },
                    {
                        "slot": "rc",
                        "branch": "offer-gradle/rc",
                        "title": "Offer Gradle 9.10.0-rc-1",
                        "pin": {
                            "stable": {
                                "version": "9.8.2",
                                "java": 25,
                                "checksums": {
                                    "distribution": "3333333333333333333333333333333333333333333333333333333333333333",
                                    "wrapper": "4444444444444444444444444444444444444444444444444444444444444444"
                                }
                            },
                            "rc": {
                                "version": "9.10.0-rc-1",
                                "java": 25,
                                "checksums": {
                                    "distribution": "5555555555555555555555555555555555555555555555555555555555555555",
                                    "wrapper": "6666666666666666666666666666666666666666666666666666666666666666"
                                }
                            },
                            "previous": {
                                "version": "8.14.6",
                                "java": 21,
                                "checksums": null
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
        val movedPrevious = listOf(Proposal(Slot.STABLE, "Offer Gradle 10.0.0 with 9.9.1 as previous major", Pin(PinnedVersion("10.0.0", 25), PinnedVersion("9.9.1", 25))))
        assertEquals(movedPrevious, ProposalsJson.decode(ProposalsJson.encode(movedPrevious)))
    }
}
