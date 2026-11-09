rootProject.name = "AlgoryQR"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Theme, components, networking and API models shared by both apps.
include(":shared")
// Owner app: dashboard, orders, tables, staff and reservations.
include(":businessApp")
// Staff app: waiter, kitchen and courier screens chosen by the signed-in role.
include(":staffApp")
