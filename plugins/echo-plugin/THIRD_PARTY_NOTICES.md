# Third-party notices

## Gradle Wrapper

This package includes unmodified `gradlew`, `gradlew.bat`, and
`gradle/wrapper/gradle-wrapper.jar` from the EvaLauncher build tooling.
Gradle is licensed under the Apache License, Version 2.0.
The scripts retain their copyright and license headers; the complete license
text is included in `META-INF/LICENSE` inside `gradle-wrapper.jar`.

Project: https://gradle.org/
Source: https://github.com/gradle/gradle
License: https://www.apache.org/licenses/LICENSE-2.0

The wrapper downloads the Gradle distribution identified in
`gradle/wrapper/gradle-wrapper.properties`. The distribution is not bundled in
this `.eva` file and contains its own license and notice files.

## Build and host dependencies

Kotlin, kotlinx.coroutines, and Ktor/kotlinx.serialization are resolved through
the repositories and versions declared in this source project. Their runtime
JARs are not included in `prebuilt/jvm/plugin.jar`; the host supplies the shared
Kotlin runtime and Eva API. The included API snapshot is Eva source, not a
third-party SDK binary.

The plugin currently has no private runtime dependencies. Adding such
dependencies requires packaging them, declaring their paths in `plugin.json`,
and updating these notices with the applicable licenses.
