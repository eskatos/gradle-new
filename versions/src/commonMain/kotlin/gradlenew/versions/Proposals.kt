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
            val keptRc = pin.rc?.takeIf { GradleVersion.parse(it.version).base > GradleVersion.parse(offered.stable.version) }
            Proposal(Slot.STABLE, "Offer Gradle ${offered.stable.version}", Pin(offered.stable, pin.previous, keptRc))
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
        return listOfNotNull(stable, rc)
    }
}
