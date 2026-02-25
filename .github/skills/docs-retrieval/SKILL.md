````skill
---
name: docs-retrieval
description: Retrieve API behavior by inspecting published source artifacts for the exact dependency version.
---

# Docs Retrieval Skill (Source Artifacts Only)

Use this skill when you need authoritative API behavior from the exact library version used by the project.

## Keywords / Triggers
- API signature unknown, behavior uncertain
- "verify contract", "what is parameter type", "what does callback do"

## Goal
Get a **verifiable answer** from published source artifacts with minimal guesswork.

## Inspect published source artifacts
1. Resolve exact dependency version from repo (`gradle/libs.versions.toml`, module build files).
2. Download `*-sources.jar` from Maven Central (or equivalent registry).
3. Locate the target source file and symbol.
4. Inspect signature and implementation behavior in source.
5. Cross-check local call sites in the codebase.

## Concrete command pattern (JVM/Gradle libs)
```bash
# 1) Download source jar for exact artifact version
curl -sSfL -o lib-sources.jar \
  https://repo1.maven.org/maven2/<group path>/<artifact>/<version>/<artifact>-<version>-sources.jar

# 2) List files and locate target symbol
jar tf lib-sources.jar | rg "<SymbolOrFileName>"

# 3) Inspect source file directly
unzip -p lib-sources.jar <path/inside/jar>.kt | sed -n '1,260p'

# 4) Extract signatures/usages fast
unzip -p lib-sources.jar <path/inside/jar>.kt | rg "fun .*<Symbol>|<paramName>:" -n
```

## Evidence quality rules
- Prefer **source-level contract** over assumptions.
- Quote/record:
  - symbol signature,
  - callback types,
  - default implementation behavior,
  - gating conditions (e.g., `isBackEnabled`).
- Distinguish:
  - branch-specific regression,
  - pre-existing behavior in `main`,
  - framework-level behavior.

## Repo-specific checklist (QuizApp)
- Check dependency versions in:
  - `gradle/libs.versions.toml`
  - `composeApp/build.gradle.kts`
- Validate call sites in:
  - `composeApp/src/commonMain/kotlin/App.kt`
  - relevant screen/component files
- If claim references branch behavior, verify `git diff main...<branch>` for the target file first.

## Anti-patterns to avoid
- Assuming callback semantics from return values when callback type is `() -> Unit`.
- Treating unverified claims as regressions without checking branch diff.
- Using version-agnostic docs when behavior may differ by alpha/beta release.

## Output template for future audits
- **Claim**: <text>
- **Verdict**: True / False / Not provable yet
- **Evidence**:
  - dependency version: <artifact:version>
  - source/docs reference: <symbol + behavior>
  - local call site: <file + behavior>
- **Action**:
  - Fix needed? yes/no
  - Minimal patch approach if yes
````
