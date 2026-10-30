package gradlenew.versions

enum class Slot(val id: String) {
    STABLE("stable"),
    RC("rc"),
    PREVIOUS("previous");

    companion object {
        fun of(id: String): Slot = requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown slot '$id'" }
    }
}

data class Proposal(val slot: Slot, val title: String, val pin: Pin) {
    val branch: String get() = "offer-gradle/${slot.id}"
}

object Proposals {

    fun of(pin: Pin, offered: Pin): List<Proposal> {
        val stable = if (offered.stable.version != pin.stable.version) {
            val newMajor = GradleVersion.parse(offered.stable.version).major != GradleVersion.parse(pin.stable.version).major
            val keptRc = pin.rc?.takeIf { GradleVersion.parse(it.version).base > GradleVersion.parse(offered.stable.version) }
            val title = "Offer Gradle ${offered.stable.version}" + if (newMajor) " with ${offered.previous.version} as previous major" else ""
            Proposal(Slot.STABLE, title, Pin(offered.stable, if (newMajor) offered.previous else pin.previous, keptRc))
        } else {
            null
        }
        val rcAfterStable = if (stable != null) stable.pin.rc else pin.rc
        val rc = if (offered.rc?.version != rcAfterStable?.version) {
            val title = if (offered.rc != null) "Offer Gradle ${offered.rc.version}" else "Stop offering Gradle ${pin.rc?.version}"
            Proposal(Slot.RC, title, pin.with(Slot.RC, offered.rc))
        } else {
            null
        }
        val previousAfterStable = stable?.pin?.previous ?: pin.previous
        val previous = if (offered.previous.version != previousAfterStable.version) {
            Proposal(Slot.PREVIOUS, "Offer Gradle ${offered.previous.version} as previous major", pin.with(Slot.PREVIOUS, offered.previous))
        } else {
            null
        }
        return listOfNotNull(stable, rc, previous)
    }
}
