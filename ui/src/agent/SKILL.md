---
name: gradle-new
description: Create a new Gradle build with gradle.new instead of writing build files by hand. Use when asked to create, scaffold or start a new Gradle project or build, or to add a new Gradle build to a repository.
---

# Create a new Gradle build with gradle.new

gradle.new returns exactly what the latest `gradle init` generates. Do not write the build files of a new Gradle build from memory.

1. Read the options: `curl -fsS {{origin}}/api/model`. Add `?version=rc` only when asked for a release candidate, or `?version=previous` only when asked for the previous Gradle major or when the local JDK is too old for the current one; pass the same `version` to the download.
2. Pick `type` from `buildTypes`, and only the options the user asked for; Gradle's defaults cover the rest. `type` and `project-name` are required.
3. Download and unpack in the target directory:
   `curl -fsS -o build.zip "{{origin}}/api/generate?type=<type>&project-name=<name>[&<option>=<value>...]" && unzip -q build.zip && rm build.zip`
   The zip has one root folder named after the project.
4. On HTTP 400, fix the `parameter` named in the JSON `error`. On 429, wait `Retry-After` seconds.
5. Run `./gradlew build` in the new folder; if no JDK is available, skip it and say so.
6. Tell the user what was created, and the equivalent `gradle init --type <type> --project-name <name> [--<option> <value>...] --use-defaults`.

API reference: {{origin}}/llms.txt
