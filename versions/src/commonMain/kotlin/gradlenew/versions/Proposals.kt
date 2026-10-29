package gradlenew.versions

enum class Slot(val id: String) {
    STABLE("stable"),
    RC("rc");

    companion object {
        fun of(id: String): Slot = requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown slot '$id'" }
    }
}

data class Proposal(val slot: Slot, val title: String, val pin: Pin) {
    val branch: String get() = "offer-gradle/${slot.id}"
}

object Proposals {

    fun of(pin: Pin, offered: Pin): List<Proposal> {
        val stable = if (offered.stable != pin.stable) {
            val keptRc = pin.rc?.takeIf { GradleVersion.parse(it).base > GradleVersion.parse(offered.stable) }
            Proposal(Slot.STABLE, "Offer Gradle ${offered.stable}", Pin(offered.stable, keptRc, offered.stableChecksums, keptRc?.let { pin.rcChecksums }))
        } else {
            null
        }
        val rcAfterStable = stable?.pin?.rc ?: pin.rc.takeIf { stable == null }
        val rc = if (offered.rc != rcAfterStable) {
            val title = if (offered.rc != null) "Offer Gradle ${offered.rc}" else "Stop offering Gradle ${pin.rc}"
            Proposal(Slot.RC, title, Pin(pin.stable, offered.rc, pin.stableChecksums, offered.rcChecksums))
        } else {
            null
        }
        return listOfNotNull(stable, rc)
    }
}
