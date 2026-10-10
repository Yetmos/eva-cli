pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "cmd-plugin"
include(":plugin-api")
project(":plugin-api").projectDir = file("sdk/plugin-api")
