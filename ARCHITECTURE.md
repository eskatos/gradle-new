# Architecture

gradle-new is a web page and an HTTP API that generate new Gradle builds. This
document is for contributors: how the system fits together, where each part
lives, and the rules that keep it that way. How to use the service is in
[README.md](README.md).

## Principles

- **Gradle is the only source of truth.** Every build is the output of the
  real `gradle init`, and the options, defaults and descriptions are read from
  Gradle itself. This project maintains no templates and no option lists.
- **No Gradle and no JVM at request time.** Everything that needs Gradle runs
  ahead of time in the build, which produces a self-contained container image.
  The hosted service is that image: a native HTTP server over pre-generated
  content.
- **One code path.** The web page and coding agents both go through the HTTP
  API. The page has no generation logic of its own.
- **A dependency-free core.** The domain logic lives in Kotlin modules that
  depend on nothing but the standard library; libraries and I/O stay in thin
  adapters at the edges.
- **Nothing provider-specific in the image.** The image is a standard OCI
  image that runs locally or on any container platform; the hosting provider
  is only a deploy target.

## The big picture

The system has two halves: publish time, inside the Gradle build of this
repository (run in CI), and request time, inside the container image.

```
PUBLISH TIME: the Gradle build

  pin file ──► for each offered Gradle version (publisher):
  versions,      1. download the distribution, verify its pinned SHA-256
  checksums,     2. read the init options model from Gradle (injected builder)
  Java           3. run `gradle init` for every combination of options,
                    with placeholder values for the free-text inputs
                 4. verify the output against real `gradle init`
                 5. write model.json and one template zip per combination
                        │
                        ▼
  container image: server binary + /site + /templates + /agent

REQUEST TIME: the image

  web page ──┐
  agents ────┼──► server ──► validate the request against the model,
  curl ──────┘               pick the template, substitute the project name,
                             package and Java version ──► zip
```

The offered versions change only through the self-update pipeline: it reads
Gradle's version feed, proposes a change to the pin as a pull request, and
once CI is green and the pull request merged, builds and deploys a new image.

## Key concepts

- **Offered versions and channels.** The service offers the latest stable
  Gradle release (the default), the latest release candidate while it is newer
  than the stable, and the latest release of the previous major (for example
  8.14.6 next to 9.x). The API names them by channel, `stable`, `rc` and
  `previous`, or by concrete version.
- **The pin.** `offered-gradle-versions.properties` at the repository root is
  the only source of offered versions for the build. For each slot it holds
  the version, the SHA-256 of its distribution and of its wrapper jar, and the
  Java that runs it. The build never reads the version feeds; only reviewed
  proposals change the pin.
- **The options model.** `model.json`, one per version: the build types,
  DSLs, test frameworks, layouts, defaults, the Java version range, the rules
  for the project name and package, and Gradle's description of every init
  option. Each id in it is the exact value the matching `gradle init` option
  accepts.
- **Templates.** The `gradle init` output for one combination of the
  enumerated options, generated with placeholder values for the three
  free-text inputs and stored as a zip under a key made of the option values
  (`java-application.kotlin.junit-jupiter.single-project.no-incubating.comments`).
- **Substitution.** Turning a template into the user's build by replacing
  the placeholders, and the forms Gradle derives from them (camel case,
  C++ namespaces), with the user's values. There is exactly one
  implementation, and every publish checks it against real `gradle init`.
- **The content tree.** What the build produces for the image: the public
  site (the page, `versions.json`, each version's `model.json`), the private
  templates, and the private agent templates.

## Publishing

The publisher is a JVM application with three commands: `publish` for one
Gradle version, `index` for `versions.json`, and `propose` for the
self-update. The build runs `publish` once per offered version, as a
separate cacheable task, so adding or changing one version leaves the
others' content to the build cache.

**Reading the options model.** `gradle init`'s choices live in Gradle
internals that no public API exposes. The `builder` mini-build provides a
Tooling API model builder that reads them (init's project layout registry,
and the `@Option` descriptions of the public `InitBuild` task class). The
publisher compiles the builder with the Gradle version under test, injects it
through an init script and requests the model through the Tooling API, so the
builder always runs against exactly the internals it was compiled against.
Gradle runs on the Java pinned for that version.

**Generating.** Each combination of the enumerated options is generated once
with placeholders that occur nowhere else: `zqxproj-name` for the project
name, `zqxpkg.zqxsub` for the package, and `JavaLanguageVersion.of(97)` for
the Java version. When a build type's output depends on the Java version in
any other way, it is generated once per Java version instead ("enumerated").

**Verifying.** Nothing is written unless every check passes for the version:

| Check | Guarantees |
|---|---|
| distribution | the downloaded distribution has the pinned SHA-256, before anything runs from it |
| model | the model was obtained and is consistent; combinations init rejects are reported |
| determinism | generating twice gives identical bytes |
| Java version | each Java build type is classified as substitutable or enumerated |
| differential | substituting real inputs into a template gives byte for byte what real `gradle init` gives |
| hygiene | no placeholder fragment survives substitution, in any file or path |
| wrapper | every template's wrapper jar has the pinned SHA-256 |
| budget | the version's templates fit the size budget (64 MB) |

The checks run first on a sample, the default combination of every build
type, so a broken Gradle release fails within seconds, then on the full
matrix. The publisher writes a report and exits 1 when a version fails.

## Serving

The server is a thin Ktor application, compiled to a GraalVM native image. It
maps HTTP requests onto calls into the domain and the results back onto
responses; it holds no domain logic and no state. It reads its port and the
locations of the site, the templates and the agent templates from the
environment.

| Endpoint | Answers |
|---|---|
| `GET /` and the site tree | the web page, `versions.json`, `v/<version>/model.json` |
| `GET /api/versions` | the offered versions |
| `GET /api/model?version=<channel or version>` | the options model |
| `GET /api/generate?type=…&project-name=…[&option=value…]` | the build, as a zip |
| `GET /llms.txt` | the API described for language models |
| `GET /.well-known/agent-skills/index.json` and `…/gradle-new/SKILL.md` | the `gradle-new` agent skill, for the `skills` CLI |

**A generate request** goes through one use case in the domain: validate the
parameters against the version's model (in init's order and wording, plus the
service's own bounds), select the template by its key, substitute the
free-text inputs, and return a zip whose single root folder is named after
the project, with `gradlew` executable. Parameter names are the `gradle init`
option names, options left out take Gradle's defaults, and `type` and
`project-name` are required. Rejections are HTTP 400 with
`{"error": "...", "parameter": "..."}`.

**Agent files.** `SKILL.md`, `llms.txt` and the skill index are rendered on
each request from templates, with the request's origin in every URL, so the
same image is correct locally and in production. The `Host` header is
validated strictly before it reaches any served text.

**Policies.**

- `/site` is public and served as is; `/templates` and `/agent` are never
  served directly.
- Answers for a concrete version are immutable; answers for a channel are
  cached briefly, since the channel moves with each release. Zips carry an
  ETag.
- Every response carries a self-only Content Security Policy, `nosniff` and
  HSTS. Requests are rate-limited per client, with a JSON 429 and
  `Retry-After` for builds.

## Web UI

The page is a client of the API, built on the Gradle Build Tool design
system:

- Plain HTML and CSS, and one ES module script split into pure functions
  (`model.js`: form state, option dependencies, validation, the command line
  and URL) and DOM code (`main.js`). No framework and no build step.
- Everything comes from `versions.json` and `model.json`: the channel choice,
  the form, its defaults and dependencies, the hints (Gradle's own
  descriptions) and client-side validation. The download calls
  `/api/generate`.
- It shows the equivalent `gradle init` command and the API URL, with only
  the options that differ from Gradle's defaults, and explains every token in
  the style of explainshell.com. A third window installs the agent skill.
- Native form controls restyled to the design system, so keyboard and label
  behaviour stay native. All assets are served from the image, listed with
  their sources in `ui/VENDORED.md`; the page makes no third-party request.

## Self-update

1. **Propose.** A workflow runs the `propose` command hourly. It reads
   Gradle's `/versions/all` feed, applies the offering rules, reads each new
   version's Java compatibility page to pick the Java that runs it, and opens
   one pull request per changed slot (stable, release candidate, previous
   major), updating it on later runs and closing it when it no longer applies.
   Feed data is treated as untrusted.
2. **Verify.** CI runs the full build on the pull request, including the
   publish and verification of every offered version and the acceptance
   journeys. A green proposal merges automatically.
3. **Publish.** A push to main builds the image, pushes it to the GitHub
   Container Registry under a tag naming its versions and commit
   (`9.8.2--8.14.6--<commit>`), deploys it, smoke-tests the live site with
   the acceptance journeys and rolls back on failure.
4. **Heartbeat.** The workflow keeps the repository active so that GitHub
   does not disable the schedule.

A failing version leaves its pull request red: that is the alarm, and main
and the live site keep serving the last good versions. Slots never block one
another.

## Code map

```
substitution, domain, versions, verification    core: Kotlin common, dependency-free
adapter-json, adapter-zip, adapter-feeds,       adapters: JVM, one concern each
adapter-tooling-api
publisher, server, ui                           applications
acceptance                                      end-to-end tests
builder                                         standalone mini-build, run by Gradle under test
build-logic                                     convention plugins
deploy                                          the only provider-specific code
```

| Module | Responsibility |
|---|---|
| `substitution` | in-memory file sets, the substitution contract, Gradle's name derivations |
| `domain` | the model types, request validation, the template catalog and keys, the generation use case, agent file rendering |
| `versions` | Gradle version ordering, the pin, the offering rules, compatibility pages, proposals |
| `verification` | the verification checks on file sets, and the report |
| `adapter-json` | every JSON format |
| `adapter-zip` | zips with Unix permissions and fixed timestamps |
| `adapter-feeds` | the services.gradle.org and docs.gradle.org clients |
| `adapter-tooling-api` | running a Gradle distribution through the Tooling API |
| `publisher` | the `publish`, `index` and `propose` commands |
| `server` | the Ktor server, its native image and the container image |
| `ui` | the page bundle and the agent templates |
| `acceptance` | the end-to-end journeys |
| `builder` | the options model builder (`builder-api`, `builder-plugin`), plain Java |
| `build-logic` | one convention plugin per module type, plain Java |
| `deploy` | provisioning and deployment scripts |

Dependencies point inwards: applications use adapters and core modules,
adapters use core modules, and core modules use only each other and the
Kotlin standard library. The build fails a core module that gains an external
dependency. This keeps the domain testable without infrastructure and the
native image free of reflection configuration.

## Build

- **One plugin per module type.** `build-logic` holds six binary plugins in
  plain Java: `gradlenew.kotlin-common-library`, `kotlin-jvm-library`,
  `kotlin-jvm-application`, `native-image-server`, `static-site` and
  `acceptance-tests`. A module's build script applies exactly one of them and
  declares its dependencies; everything else is the plugin's job. A module
  that needs more is a sign of a missing convention or a new module type.
- **Content moves between modules as variants.** The site, the templates,
  the agent templates, the image reference and the publisher's classpath
  travel through dedicated configurations and outgoing variants, which is
  what isolated projects allow.
- **Conventions.**
  - The build cache and isolated projects are on, and every plugin must
    support them.
  - All versions are in `gradle/libs.versions.toml`, kept at the latest
    release by automated pull requests, the wrapper included.
  - The project builds on JDK 25, provisioned by the Foojay toolchain
    resolver.
  - Each offered Gradle version runs on the Java its pin slot names,
    provisioned the same way.
  - Kotlin warnings are errors.
- **The image.** The `Dockerfile` compiles the native image in a GraalVM
  builder stage, so no host needs GraalVM, and copies a mostly static binary
  onto a distroless runtime with no shell, running as a non-root user. Both
  base images are pinned by digest. Layers go from least to most frequently
  changed: binary, templates, agent templates, site.

## Testing

- Most tests are unit tests of the core modules, without infrastructure.
  Adapters are tested against the real thing: real zips, real Gradle
  distributions. The page is tested in WebKit, Firefox and Chromium through
  Playwright.
- A few long acceptance journeys cover the whole system:
  - the API journey, comparing generated builds with real `gradle init` for
    every offered version;
  - the UI journey, driving the page in all three engines;
  - the publisher scenario, running the publisher on recorded feeds and on
    deliberately broken Gradle output.
- The API and UI journeys run against the built image (started through
  Docker or Podman) or against a URL, which is how a deployment is
  smoke-tested.
- Tests that need a specific Gradle version name it; the others follow the
  pin.

## Hosting

- One Scaleway Serverless Container in Europe, scaling to zero, with the
  platform's TLS certificate for the custom domain. Usage stays within the
  free tier, with a billing alert as a safety net.
- The image is pulled from the GitHub Container Registry by its unique tag;
  there is one environment, and a push to main deploys it.
- The only state is the deployed image. Locally the same image runs with
  `docker run -p 8080:8080`, and only `deploy/` names the provider.

## Rules

- User input reaches only the substitution, never a shell or Gradle.
  Inputs are bounded and validated before any work.
- Generation is deterministic: the same request always gives the same bytes,
  and zips keep `gradlew` executable.
- Placeholders never collide with anything Gradle generates; every publish
  checks it.
- Converting existing Maven builds (`gradle init --type pom`) is out of
  scope.

## Dependencies

The set of external dependencies is an architecture decision: adding one, in
any module, production or test, requires the maintainer's approval and is
recorded here.

- Kotlin standard library and kotlin-test: all Kotlin modules.
- kotlinx-serialization-json: `adapter-json`.
- gradle-tooling-api, slf4j-api with a binding: `adapter-tooling-api`.
- Ktor server core, the CIO engine, the rate-limit plugin and a logging
  binding: `server`; Ktor's test host in its tests.
- JUnit platform, Testcontainers and Playwright for Java: `acceptance`;
  Playwright for Java and JUnit Jupiter: `ui` tests.
- Build plugins: the Kotlin Gradle plugin and the kotlinx-serialization
  plugin (`build-logic`), the version catalog update plugin (root build), the
  Foojay toolchain resolver convention (settings).
- Java toolchains provisioned through that resolver: JDK 25 for the project,
  and the Java each pinned Gradle version runs on.
- Base images, pinned by digest: `gcr.io/distroless/base-nossl-debian13:nonroot`
  and `ghcr.io/graalvm/native-image-community:25`.
- Data sources of the `propose` command: `/versions/all` on
  services.gradle.org and each new version's compatibility page on
  docs.gradle.org.
- GitHub Actions, each pinned by full commit SHA: `actions/checkout`,
  `gradle/actions/setup-gradle`, `gradle/actions/wrapper-validation`,
  `actions/upload-artifact`, `actions/attest-build-provenance`,
  `actions/cache`; setup-gradle's enhanced cache provider, a proprietary
  component free for public repositories.
- Tools: Docker or Podman, the Scaleway CLI, the GitHub CLI.
- Vendored in `ui`, each with its source and version in `ui/VENDORED.md`:
  the Gradle Build Tool design system's tokens and the Gradle logos,
  Develocity's logo, Lato and JetBrains Mono (SIL Open Font License), and
  Lucide icons (ISC licence).
