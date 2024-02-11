import org.gradle.configurationcache.extensions.capitalized
import java.io.ByteArrayOutputStream

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
    id("com.bmuschko.docker-java-application") version "9.4.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
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
    variables: [random, network, behavior, devices, load, computationalCost, simulationSeconds]
""".trimIndent()

fun graphicsAlchemistConfiguration(effectName: String) = """
    launcher:
      type: SwingGUI
      parameters:
        graphics: effects/$effectName.json
""".trimIndent()

val simulationFiles = File(rootProject.rootDir.path + "/src/main/yaml").listFiles()
    ?.filter { it.extension == "yml" } // pick all yml files in src/main/yaml
    ?.sortedBy { it.nameWithoutExtension } // sort them, we like reproducibility

val runAllGraphic by tasks.register<DefaultTask>("runAllGraphic") {
    group = alchemistGroup
    description = "Launches all simulations with the graphic subsystem enabled"
}
val runAllBatch by tasks.register<DefaultTask>("runAllBatch") {
    group = alchemistGroup
    description = "Launches all experiments"
}

// Heap size estimation for batches
val maxHeap: Long? by project
val heap: Long = maxHeap ?: if (System.getProperty("os.name").lowercase().contains("linux")) {
    ByteArrayOutputStream().use { output ->
        exec {
            executable = "bash"
            args = listOf("-c", "cat /proc/meminfo | grep MemAvailable | grep -o '[0-9]*'")
            standardOutput = output
        }
        output.toString().trim().toLong() / 1024
    }.also { println("Detected ${it}MB RAM available.") } * 9 / 10
} else {
    // Guess 16GB RAM of which 2 used by the OS
    14 * 1024L
}
val taskSizeFromProject: Int? by project
val taskSize = taskSizeFromProject ?: 512
val threadCount = maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), heap.toInt() / taskSize))

/*
 * Scan the folder with the simulation files, and create a task for each one of them.
 */
File(rootProject.rootDir.path + "/src/main/yaml").listFiles()
    ?.filter { it.extension == "yml" }
    ?.sortedBy { it.nameWithoutExtension }
    ?.forEach {
        fun basetask(name: String, additionalConfiguration: JavaExec.() -> Unit = {}) = tasks.register<JavaExec>(name) {
            group = alchemistGroup
            description = "Launches graphic simulation ${it.nameWithoutExtension}"
            mainClass.set("it.unibo.alchemist.Alchemist")
            classpath = sourceSets["main"].runtimeClasspath
            args("run", it.absolutePath)
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(17))
                },
            )
            if (System.getenv("CI") == "true") {
                args("--override", "terminate: { type: AfterTime, parameters: [2] } ")
            } else {
                this.additionalConfiguration()
            }
        }
        val capitalizedName = it.nameWithoutExtension.capitalized()
        val graphic by basetask("run${capitalizedName}Graphic") {
            args(
                "--override",
                "monitors: { type: SwingGUI, parameters: { graphics: effects/${it.nameWithoutExtension}.json } }",
                "--override",
                "launcher: { parameters: { batch: [], autoStart: false } }",
            )
        }
        runAllGraphic.dependsOn(graphic)
        val batch by basetask("run${capitalizedName}Batch") {
            description = "Launches batch experiments for $capitalizedName"
            maxHeapSize = "${minOf(heap.toInt(), Runtime.getRuntime().availableProcessors() * taskSize)}m"
            File("data").mkdirs()
        }
        runAllBatch.dependsOn(batch)
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
        jvmArgs = listOf("-Xms256m", "-Xmx200g")
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
