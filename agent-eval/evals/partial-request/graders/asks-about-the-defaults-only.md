---
type: llm
---

PASS if the final message asks whether Gradle's defaults are fine for the remaining options of a Kotlin library named demo, and does not ask again what kind of build to create or what to name it.
FAIL if it asks for the kind of build or the project name, or does not ask about the defaults.
