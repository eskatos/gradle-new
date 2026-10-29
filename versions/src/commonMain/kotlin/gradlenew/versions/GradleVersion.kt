package gradlenew.versions

sealed interface Stage : Comparable<Stage> {

    val rank: Int

    data class Snapshot(val timestamp: String) : Stage {
        override val rank get() = 0
    }

    data class Milestone(val number: Int, val suffix: String) : Stage {
        override val rank get() = 1
    }

    data class ReleaseCandidate(val number: Int) : Stage {
        override val rank get() = 2
    }

    data object Final : Stage {
        override val rank get() = 3
    }

    override fun compareTo(other: Stage): Int {
        if (rank != other.rank) return rank.compareTo(other.rank)
        return when (this) {
            is Snapshot -> timestamp.compareTo((other as Snapshot).timestamp)
            is Milestone -> compareValuesBy(this, other as Milestone, { it.number }, { it.suffix })
            is ReleaseCandidate -> number.compareTo((other as ReleaseCandidate).number)
            Final -> 0
        }
    }
}

data class GradleVersion(val parts: List<Int>, val stage: Stage) : Comparable<GradleVersion> {

    init {
        require(parts.size >= 2 && parts.all { it >= 0 }) { "A Gradle version has at least a major and a minor number" }
    }

    val base: GradleVersion get() = GradleVersion(parts, Stage.Final)

    val isFinal: Boolean get() = stage == Stage.Final

    val isReleaseCandidate: Boolean get() = stage is Stage.ReleaseCandidate

    override fun compareTo(other: GradleVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return stage.compareTo(other.stage)
    }

    override fun toString(): String = parts.joinToString(".") + when (stage) {
        Stage.Final -> ""
        is Stage.ReleaseCandidate -> "-rc-${stage.number}"
        is Stage.Milestone -> "-milestone-${stage.number}${stage.suffix}"
        is Stage.Snapshot -> "-${stage.timestamp}"
    }

    companion object {
        private val FORM = Regex("""(\d+(?:\.\d+)+)(?:-(?:rc-(\d+)|milestone-(\d+)([a-z]?)|(\d{14}\+\d{4})))?""")

        fun parseOrNull(text: String): GradleVersion? {
            val match = FORM.matchEntire(text) ?: return null
            val (numbers, rc, milestone, suffix, timestamp) = match.destructured
            val stage = when {
                rc.isNotEmpty() -> Stage.ReleaseCandidate(rc.toInt())
                milestone.isNotEmpty() -> Stage.Milestone(milestone.toInt(), suffix)
                timestamp.isNotEmpty() -> Stage.Snapshot(timestamp)
                else -> Stage.Final
            }
            return GradleVersion(numbers.split('.').map { it.toInt() }, stage)
        }

        fun parse(text: String): GradleVersion =
            requireNotNull(parseOrNull(text)) { "'$text' is not a Gradle version" }
    }
}
