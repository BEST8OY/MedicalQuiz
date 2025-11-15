Gradle Remote Build Cache (optional)

You can set up a remote Gradle build cache to share build outputs across CI runs and between machines for big improvements.

1. Choose a remote cache implementation:
   - Gradle Enterprise (recommended for large teams)
   - Artifactory or other HTTP-based cache

2. Example settings for `settings.gradle.kts`:

```kotlin
buildCache {
  local { isEnabled = true }
  remote<HttpBuildCache> {
    url = uri(System.getenv("GRADLE_REMOTE_CACHE_URL"))
    isPush = true
    credentials {
      username = System.getenv("GRADLE_REMOTE_CACHE_USER")
      password = System.getenv("GRADLE_REMOTE_CACHE_PASSWORD")
    }
  }
}
```

3. In GitHub Actions, configure secrets:
   - `GRADLE_REMOTE_CACHE_URL`
   - `GRADLE_REMOTE_CACHE_USER`
   - `GRADLE_REMOTE_CACHE_PASSWORD`

4. Add `--build-cache` to your Gradle command or enable `org.gradle.caching=true` in `gradle.properties`.

Notes:
- Remote cache gives the largest wins on large codebases or multi-module Android apps.
- Cache misses depend on changed outputs and inputs; set up proper cache keys in actions-cache for artifacts.
