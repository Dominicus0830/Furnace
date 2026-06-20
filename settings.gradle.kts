pluginManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
}

rootProject.name = "Furnace"
include("furnace-api", "furnace-server", "furnace-plugin-test", "worldgen-oracle")