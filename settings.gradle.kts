pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "eva-cli"

include(":core")
include(":cli")
include(":plugins:echo-plugin")
include(":plugins:cmd-plugin")
include(":plugins:cmd-plugin-api")
include(":plugins:root-plugin")
