# gradle.new

Create a new Gradle build from your browser, with a single HTTP request, or by
asking your coding agent.

gradle.new runs the real `gradle init` of the latest Gradle release, so what you
download is exactly what `gradle init` would produce on your machine, for every
option it supports.

## Features

- Every option of `gradle init` for new builds: build type, language, build
  script DSL, test framework, project name, package, target Java version, single
  or multi-project layout, incubating APIs, clarifying comments.
- Latest stable Gradle by default, plus the latest release candidate when one is
  available for a higher version, and the latest release of the previous major
  version for builds that cannot move to the current one yet.
- Always current. New Gradle releases are picked up automatically.
- Web UI following the Gradle Build Tool design system, showing the equivalent
  `gradle init` command and API URL for your choices.
- HTTP API for scripts and tools.
- An agent skill, `gradle-new`, so that coding agents create new builds with
  gradle.new instead of writing build files from memory.

## HTTP API

```
curl -fsS -o demo.zip "https://<site>/api/generate?type=java-application&project-name=demo"
```

- `GET /api/versions`: the offered Gradle versions, `stable`, `rc` (or `null`)
  and `previous`.
- `GET /api/model`: the options of `gradle init` for a version: build types,
  DSLs, test frameworks, defaults and Gradle's description of every option.
- `GET /api/generate`: the build, as a zip with one root folder named after the
  project. Parameters are the `gradle init` option names; `type` and
  `project-name` are required, and every other option takes Gradle's default.
- `version` selects the Gradle version for both: `stable` (the default), `rc`,
  `previous`, or an offered version number.
- Invalid requests get HTTP 400 with `{"error": "...", "parameter": "..."}`.
  Requests are limited per client; above the limit, HTTP 429 with
  `Retry-After`.

The same description, written for language models, is served at `/llms.txt`.

## Agent skill

The page shows a command that installs the `gradle-new` skill once for your
coding agents, either with the open-source `skills` CLI
(`npx skills add https://<site> -g`, which needs Node.js 22.20 or later) or with
`curl`. Then ask your agent for a new Gradle project.

## Status

Early development, not yet deployed.

## Why

The existing [Gradle Initializr](https://gradle-initializr.cleverapps.io/)
([source](https://github.com/bmuschko/gradle-initializr)) is unmaintained and no
longer matches what `gradle init` offers. gradle.new generates builds with
Gradle itself instead of maintaining its own templates, so it cannot drift.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) describes how the service works.

## License

[Apache License 2.0](LICENSE). The vendored fonts and icons keep their own
licences, listed in [ui/VENDORED.md](ui/VENDORED.md).

Gradle®, Develocity®, Build Scan® and the Gradlephant logo are registered
trademarks of Gradle, Inc. This project is not affiliated with or endorsed by
Gradle, Inc.
