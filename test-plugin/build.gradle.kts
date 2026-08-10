version = "1.0.0-SNAPSHOT"

val craftuxVersion = rootProject.providers.gradleProperty("craftuxVersion").orElse("1.0.3")

dependencies {
    compileOnly(project(":alkahest-api"))
    // CraftUX is supplied to the development server by runAlkahest.
    compileOnly("dev.craftux:craftux-paper:${craftuxVersion.get()}")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "apiversion" to "\"${rootProject.providers.gradleProperty("apiVersion").get()}\"",
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
