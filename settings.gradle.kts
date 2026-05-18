pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "AIP396 AI Learn Language"
include(":app")
include(":sample")
// :ai-speech is published as `apero-inhouse:ai-speech` (see scripts/publish.sh).
// :sample consumes the published artifact from mavenLocal to act as an integration check.
// Re-add `include(":ai-speech")` if you need to iterate on the module without republishing.
// include(":ai-speech")
