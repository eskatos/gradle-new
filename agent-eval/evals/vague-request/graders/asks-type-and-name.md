---
type: llm
---

PASS if the final message asks the user which kind of build to create (for example an application, a library, a Gradle plugin or a basic build, in some language) and asks for a project name or proposes one for confirmation, without claiming that a build was created.
FAIL if it creates or claims to have created a build, or picks the kind of build or the name without asking.
