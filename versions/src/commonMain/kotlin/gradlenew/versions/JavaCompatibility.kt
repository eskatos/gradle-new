package gradlenew.versions

object JavaCompatibility {

    private const val JAVA_COLUMN = "Java version"
    private const val RUNNING_COLUMN = "Support for running Gradle"
    private val TABLE = Regex("""<table[^>]*>([\s\S]*?)</table>""")
    private val ROW = Regex("""<tr[^>]*>([\s\S]*?)</tr>""")
    private val CELL = Regex("""<t[hd][^>]*>([\s\S]*?)</t[hd]>""")
    private val TAG = Regex("""<[^>]+>""")
    private val SPACE = Regex("""\s+""")
    private val SINCE = Regex("""(\d+(?:\.\d+)+)(?: and after)?""")
    private val RANGE = Regex("""(\d+(?:\.\d+)+) to (\d+)\.(\d+)\.x""")

    fun highestLts(page: String, version: String): Int =
        requireNotNull(runsOn(page, version).filter(::isLts).maxOrNull()) { "The compatibility page lists no long-term support Java that runs Gradle $version" }

    fun runsOn(page: String, version: String): List<Int> {
        val gradle = GradleVersion.parse(version).base
        val rows = table(page)
        val header = rows.first()
        val javaColumn = header.indexOf(JAVA_COLUMN)
        val runningColumn = header.indexOf(RUNNING_COLUMN)
        require(javaColumn >= 0) { "The compatibility table has no '$JAVA_COLUMN' column" }
        return rows.drop(1).mapNotNull { row ->
            val java = requireNotNull(row.getOrNull(javaColumn)?.toIntOrNull()) { "The compatibility table has a row without a Java version: $row" }
            val running = requireNotNull(row.getOrNull(runningColumn)) { "The compatibility table has no '$RUNNING_COLUMN' for Java $java" }
            java.takeIf { runs(gradle, running) }
        }.sorted()
    }

    fun isLts(java: Int): Boolean = java == 8 || java == 11 || (java >= 17 && (java - 17) % 4 == 0)

    private fun table(page: String): List<List<String>> {
        val tables = TABLE.findAll(page).map { table -> ROW.findAll(table.groupValues[1]).map { row -> CELL.findAll(row.groupValues[1]).map { text(it.groupValues[1]) }.toList() }.toList() }
        return requireNotNull(tables.firstOrNull { rows -> rows.firstOrNull()?.contains(RUNNING_COLUMN) == true }) {
            "The compatibility page has no table with a '$RUNNING_COLUMN' column"
        }
    }

    private fun runs(gradle: GradleVersion, cell: String): Boolean {
        if (cell == "N/A") return false
        RANGE.matchEntire(cell)?.let { range ->
            val (since, untilMajor, untilMinor) = range.destructured
            val untilReached = gradle.major < untilMajor.toInt() || (gradle.major == untilMajor.toInt() && gradle.parts[1] <= untilMinor.toInt())
            return gradle >= GradleVersion.parse(since) && untilReached
        }
        val since = requireNotNull(SINCE.matchEntire(cell)) { "The compatibility table has an unknown '$RUNNING_COLUMN' value: '$cell'" }
        return gradle >= GradleVersion.parse(since.groupValues[1])
    }

    private fun text(html: String): String =
        html.replace(TAG, "").replace("&nbsp;", " ").replace("&amp;", "&").replace(SPACE, " ").trim()
}
