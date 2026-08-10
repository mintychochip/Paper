import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.register
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("io.papermc.paperweight.core") version "2.0.0-beta.21" apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"
val craftuxMavenUrl = "https://maven.pkg.github.com/aincraft-org/craftux"
val craftuxVersion = providers.gradleProperty("craftuxVersion").orElse("1.0.3")
val craftuxPackageUser = providers.gradleProperty("craftuxRepoUser")
    .orElse(providers.gradleProperty("gpr.user"))
    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
    .orElse("")
val craftuxPackageToken = providers.gradleProperty("craftuxRepoToken")
    .orElse(providers.gradleProperty("gpr.key"))
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
    .orElse("")

fun org.gradle.api.artifacts.dsl.RepositoryHandler.addCraftuxRepository() {
    maven {
        name = "CraftuxGitHubPackages"
        url = uri(craftuxMavenUrl)
        credentials {
            username = craftuxPackageUser.get()
            password = craftuxPackageToken.get()
        }
    }
}

repositories {
    addCraftuxRepository()
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
        options.isFork = true
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources>().configureEach {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test>().configureEach {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }

    repositories {
        mavenCentral()
        maven(paperMavenPublicUrl)
        addCraftuxRepository()
    }
}



tasks.register("printMinecraftVersion") {
    val mcVersion = providers.gradleProperty("mcVersion")
    doLast {
        println(mcVersion.get().trim())
    }
}

tasks.register("printAlkahestVersion") {
    val alkahestVersion = provider { project.version }
    doLast {
        println(alkahestVersion.get())
    }
}
