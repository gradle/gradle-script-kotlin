## Kotlin versions and the Gradle Kotlin DSL 

Gradle ships with Kotlin DSL support which uses embedded Kotlin compiler, plus fixed Kotlin `stdlib` and `reflect`
dependencies. For example, Gradle 4.3 ships with Kotlin 1.1.51, as well as `stdlib` and `reflect` with the same version.
The `kotlin` package from those modules is visible through the Gradle classpath.

The [compatibility guarantees](https://kotlinlang.org/docs/reference/compatibility.html) provided by Kotlin apply for
both backward and forward compatibility.

### Backward compatibility

Our approach is to only do backwards-breaking Kotlin upgrades on a major Gradle release. We will always clearly document
which Kotlin version we ship and announce upgrade plans before a major release. 

Plugin authors who want to stay compatible with older Gradle versions need to limit their API usage to a subset that is
compatible with these old versions. It’s not really different from any other new API in Gradle. E.g. if we introduce a
new API for dependency resolution and a plugin wants to use that API, then they either need to drop support for older
Gradle versions or they need to do some clever organization of their code to only execute the new code path on newer
versions.

### Forward compatibility

The biggest issue is the compatibility between the external `kotlin-gradle-plugin` version and the `kotlin-stdlib`
version shipped with Gradle. More generally, between any plugin that transitively depends on `kotlin-stdlib` and its
version shipped with Gradle. As long as the combination is compatible everything should work. This will become less of
an issue as the language matures.
