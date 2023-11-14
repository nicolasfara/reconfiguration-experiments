import org.gradle.configurationcache.extensions.capitalized

/*
 * DEFAULT GRADLE BUILD FOR ALCHEMIST SIMULATOR
 */

plugins {
    application
    scala
    alias(libs.plugins.multiJvmTesting) // Pre-configures the Java toolchains
    alias(libs.plugins.taskTree) // Helps debugging dependencies among gradle tasks
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinx.serialization)
    id("com.bmuschko.docker-java-application") version "8.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Check the catalog at gradle/libs.versions.gradle
    implementation(libs.bundles.alchemist)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.arrow)
    implementation(libs.kotlinx.dataframe)
    implementation(libs.kotlinx.kandy)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

multiJvm {
    jvmVersionForCompilation.set(17)
}

val batch: String by project
val maxTime: String by project

val alchemistGroup = "Run Alchemist"
/*
 * This task is used to run all experiments in sequence
 */
val runAll by tasks.register<DefaultTask>("runAll") {
    group = alchemistGroup
    description = "Launches all simulations"
}

val ciAlchemistConfiguration = """
    terminate:
      - type: AfterTime
        parameters: $maxTime
""".trimIndent()

val batchAlchemistConfiguration = """
launcher:
  type: HeadlessSimulationLauncher
  parameters:
    parallelism: ${Runtime.getRuntime().availableProcessors() - 1}
    variables: [random, network, behavior, devices, load, computationalCost]
""".trimIndent()

fun graphicsAlchemistConfiguration(effectName: String) = """
    launcher:
      type: SingleRunSwingUI
      parameters:
        graphics: effects/$effectName.json
""".trimIndent()

val simulationFiles = File(rootProject.rootDir.path + "/src/main/yaml").listFiles()
    ?.filter { it.extension == "yml" } // pick all yml files in src/main/yaml
    ?.sortedBy { it.nameWithoutExtension } // sort them, we like reproducibility

/*
 * Scan the folder with the simulation files, and create a task for each one of them.
 */

simulationFiles?.forEach {
    // one simulation file -> one gradle task
    val task by tasks.register<JavaExec>("run${it.nameWithoutExtension.capitalized()}") {
        group = alchemistGroup // This is for better organization when running ./gradlew tasks
        description = "Launches simulation ${it.nameWithoutExtension}" // Just documentation
        mainClass.set("it.unibo.alchemist.Alchemist") // The class to launch
        classpath = sourceSets["main"].runtimeClasspath // The classpath to use
        // Uses the latest version of java
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(multiJvm.latestJava))
            },
        )
        jvmArgs("-Dsun.java2d.opengl=true")
        // These are the program arguments
        args("run", it.absolutePath, "--override")
        when {
            // If it is running in a Continuous Integration environment, use the "headless" mode of the simulator
            // Namely, force the simulator not to use graphical output.
            System.getenv("CI") == "true" -> args(ciAlchemistConfiguration)
            // If it is running in batch mode, use the "headless" mode of the simulator with the variables specified
            // in the 'batchAlchemistConfiguration'
            batch == "true" -> args(batchAlchemistConfiguration)
            // A graphics environment should be available, so load the effects for the UI from the "effects" folder
            // Effects are expected to be named after the simulation file
            else -> args(graphicsAlchemistConfiguration(it.nameWithoutExtension))
        }
    }
    // task.dependsOn(classpathJar) // Uncomment to switch to jar-based classpath resolution
    runAll.dependsOn(task)
}

val runAllLoadBased by tasks.register<DefaultTask>("runAllLoadBased") {
    group = alchemistGroup
    description = "Launches all load-based simulations"

    tasks.filter { it.name.startsWith("runLoadBased") }.forEach {
        dependsOn(it)
    }
}

docker {
    javaApplication {
        baseImage = "eclipse-temurin:${multiJvm.jvmVersionForCompilation.get()}"
        maintainer = "Nicolas Farabegoli <nicolas.farabegoli@unibo.it>"
        jvmArgs = listOf("-Xms256m", "-Xmx2048m")
        mainClassName = "it.unibo.alchemist.Alchemist"
        images = setOf("nicolasfarabegoli/reconfiguration-experiments:latest")
    }
}

val simulationFileName = "loadBasedReconfiguration.yml"

tasks.dockerCreateDockerfile {
    simulationFiles?.forEach {
        copyFile(it.name, ".")
    }
    defaultCommand(
        "run",
        simulationFileName,
        "--override",
        batchAlchemistConfiguration.replace("\n", "\\n"),
    )

    doLast {
        simulationFiles?.forEach {
            copy {
                from(it.absolutePath)
                into(layout.buildDirectory.dir("docker"))
            }
        }
    }
}

tasks.withType<Tar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.WARN
}
tasks.withType<Zip>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.WARN
}
