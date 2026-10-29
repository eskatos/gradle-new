package gradlenew.substitution

import kotlin.test.Test
import kotlin.test.assertEquals

class DerivationsTest {

    private val gradle982 = listOf(
        Triple("my-cool-thing", "MyCoolThing", "my_cool_thing"),
        Triple("my.cool thing", "MyCoolThing", "my_cool_thing"),
        Triple("my_cool-thing", "My_coolThing", "my_cool_thing"),
        Triple("demo2-app", "Demo2App", "demo2_app"),
        Triple("app", "App", "app"),
        Triple("a-bc", "ABc", "a_bc"),
        Triple("alreadyCamel", "AlreadyCamel", "alreadyCamel"),
        Triple("--leading", "Leading", "leading"),
        Triple("trailing--", "Trailing", "trailing"),
        Triple("a--b", "AB", "a_b"),
        Triple("", "", ""),
        Triple("-", "", ""),
        Triple("café-bar", "CafBar", "café_bar"),
        Triple("café", "Caf", "café"),
        Triple("9-x", "9X", "x"),
        Triple("app-2x", "App2x", "app_x"),
        Triple("under_score", "Under_score", "under_score"),
        Triple("demo.app", "DemoApp", "demo_app"),
        Triple("--a--b--", "AB", "a_b"),
        Triple("Demo-App", "DemoApp", "Demo_App"),
    )

    @Test
    fun camelCaseMatchesGradleTextUtil() {
        for ((name, camel, _) in gradle982) {
            assertEquals(camel, Derivations.toCamelCase(name), "toCamelCase(\"$name\")")
        }
    }

    @Test
    fun capitalisedCamelCaseMatchesGradlePluginClassNames() {
        for ((name, camel, _) in gradle982) {
            assertEquals(camel, Derivations.capitalize(Derivations.toCamelCase(name)), "capitalize(toCamelCase(\"$name\"))")
        }
    }

    @Test
    fun namespaceMatchesGradleNamespaceBuilder() {
        for ((name, _, namespace) in gradle982) {
            assertEquals(namespace, Derivations.toNamespace(name), "toNamespace(\"$name\")")
        }
    }

    @Test
    fun capitaliseUppercasesOnlyTheFirstCharacter() {
        assertEquals("Demo", Derivations.capitalize("demo"))
        assertEquals("DEMO", Derivations.capitalize("DEMO"))
        assertEquals("", Derivations.capitalize(""))
    }
}
