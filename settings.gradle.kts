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
include(":examples:echo-plugin")
include(":examples:cmd-plugin")
