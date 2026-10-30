package gradlenew.ui

import java.io.File

object Fixtures {

    private fun resource(path: String): File = File(checkNotNull(Fixtures::class.java.getResource("/$path")) { "missing fixture $path" }.toURI())

    val model: String get() = resource("fixture/v/9.8.2/model.json").readText()
    val previousModel: String get() = resource("fixture/v/8.14.6/model.json").readText()
    val versions: String get() = resource("fixture/versions.json").readText()
    val versionsWithRc: String get() = resource("fixture/versions-with-rc.json").readText()
    val fixtureDir: File get() = resource("fixture")
    val harness: File get() = resource("harness/index.html")
}
