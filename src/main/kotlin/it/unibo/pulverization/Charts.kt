//@file:Suppress("DestructuringDeclarationWithTooManyEntries")
//
//package it.unibo.pulverization
//
//import org.jetbrains.kotlinx.dataframe.DataFrame
//import org.jetbrains.kotlinx.dataframe.api.add
//import org.jetbrains.kotlinx.dataframe.api.dropNA
//import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame
//import org.jetbrains.kotlinx.dataframe.api.mapToColumn
//import org.jetbrains.kotlinx.dataframe.io.ColType
//import org.jetbrains.kotlinx.dataframe.io.readCSV
//import org.jetbrains.kotlinx.dataframe.io.writeCSV
//import org.jetbrains.kotlinx.kandy.dsl.invoke
//import org.jetbrains.kotlinx.kandy.dsl.plot
//import org.jetbrains.kotlinx.kandy.letsplot.export.save
//import org.jetbrains.kotlinx.kandy.letsplot.layers.line
//import org.jetbrains.kotlinx.kandy.letsplot.layout
//import org.jetbrains.kotlinx.kandy.letsplot.x
//import org.jetbrains.kotlinx.kandy.util.color.Color
//import java.nio.file.Files
//import java.nio.file.Path
//import java.nio.file.StandardOpenOption
//import kotlin.io.path.Path
//import kotlin.io.path.absolutePathString
//import kotlin.io.path.listDirectoryEntries
//import kotlin.io.path.name
//import kotlin.io.path.readLines
//
//data class Scenario(val dataframe: DataFrame<*>, val devices: String, val load: String, val scenario: String)
//
//fun main() {
//    val dataPath = Path("data")
//    val chartsPath = Path(dataPath.absolutePathString() + "/charts")
//    val resultsPath = Path(dataPath.absolutePathString() + "/results")
//    resultsPath.toFile().mkdirs()
//
//    val header = listOf(
//        "time", "canOffload[sum]", "wantToOffload[sum]", "effectiveLoad[mean]", "effectiveLoad[max]",
//        "effectiveLoad[min]", "latency[mean]", "latency[max]", "latency[min]",
//    )
//    val colTypes = mapOf(
//        "time" to ColType.Double,
//        "canOffload[sum]" to ColType.Double,
//        "wantToOffload[sum]" to ColType.Double,
//        "effectiveLoad[mean]" to ColType.Double,
//        "effectiveLoad[max]" to ColType.Double,
//        "effectiveLoad[min]" to ColType.Double,
//        "latency[mean]" to ColType.Double,
//        "latency[max]" to ColType.Double,
//        "latency[min]" to ColType.Double,
//    )
//
//    val dataframes = dataPath.listDirectoryEntries("*.csv")
//        .map { removeHeaderFromCsvFile(it) }
//        .map {
//            val scenarioRegex = Regex("it.unibo.pulverization.load.(.+),+")
//            val devicesRegex = Regex(".+devices-(\\d+\\.\\d)")
//            val scenario = scenarioRegex.find(it.name)!!.groupValues[1]
//            val devices = devicesRegex.find(it.name)!!.groupValues[1]
//            val loadRegex = Regex(".+cost-(\\d+\\.\\d)")
//            val load = loadRegex.find(it.name)!!.groupValues[1]
//            val df = DataFrame.readCSV(it.toFile(), delimiter = ' ', header = header, colTypes = colTypes)
//            Scenario(df, devices, load, scenario)
//        }.toList()
//
//    val qosDataframe = dataframes.fold(emptyDataFrame<Any>()) { acc, (df, devices, load, name) ->
//        val data = df["time", "canOffload[sum]", "wantToOffload[sum]"]
//        val offloadingCol = data.mapToColumn("offloadingQoS[$devices, $load, $name]") {
//            runCatching { "canOffload[sum]"<Int>() / "wantToOffload[sum]"<Int>().toDouble() }
//                .getOrElse { Double.NaN }
//        }
//        val newDf = if (!acc.containsColumn("time")) acc.add(data["time"]) else acc
//        newDf.add(offloadingCol)
//    }.dropNA()
//    val latencyDataFrame = dataframes.fold(emptyDataFrame<Any>()) { acc, (df, devices, load, name) ->
//        val latencyCol = df["latency[mean]"].rename("latency[$devices, $load, $name]")
//        val newDf = if (!acc.containsColumn("time")) acc.add(df["time"]) else acc
//        newDf.add(latencyCol)
//    }.dropNA()
//    val effectiveLoadMean = dataframes.fold(emptyDataFrame<Any>()) { acc, (df, devices, load, name) ->
//        val effectiveLoadCol = df["effectiveLoad[mean]"].rename("effectiveLoadMean[$devices, $load, $name]")
//        val newDf = if (!acc.containsColumn("time")) acc.add(df["time"]) else acc
//        newDf.add(effectiveLoadCol)
//    }.dropNA()
//
//    qosDataframe.writeCSV(resultsPath.absolutePathString() + "/offloading_qos.csv")
//    latencyDataFrame.writeCSV(resultsPath.absolutePathString() + "/latencies.csv")
//    effectiveLoadMean.writeCSV(resultsPath.absolutePathString() + "/effective_load_mean.csv")
//
//    // Plotting ---------------------------------------------------------------------------------------------
//
//    val colors = listOf(Color.ORANGE, Color.LIGHT_BLUE, Color.GREEN, Color.PURPLE, Color.YELLOW)
//
//    plot(qosDataframe) {
//        layout {
//            title = "Offloading QoS"
//            xAxisLabel = "Time (s)"
//            yAxisLabel = "Offloading QoS"
//        }
//        x("time"<Double>())
//        qosDataframe.columnNames().filter { it != "time" }.zip(colors).forEach { (col, clr) ->
//            line {
//                y(col<Double>())
//                width = 1.0
//                color = clr
//            }
//        }
//    }.save("offloading_qos.html", path = chartsPath.absolutePathString())
//
//    plot(latencyDataFrame) {
//        layout {
//            title = "Average latency of the system"
//            xAxisLabel = "Time (s)"
//            yAxisLabel = "Latency (ms)"
//        }
//        x("time"<Double>())
//        latencyDataFrame.columnNames().filter { it != "time" }.zip(colors).forEach { (col, clr) ->
//            line {
//                y(col<Double>())
//                width = 1.0
//                color = clr
//            }
//        }
//    }.save("latencies.html", path = chartsPath.absolutePathString())
//
//    plot(effectiveLoadMean) {
//        layout {
//            title = "Average effective load of the system"
//            xAxisLabel = "Time (s)"
//            yAxisLabel = "Effective load"
//        }
//        x("time"<Double>())
//        effectiveLoadMean.columnNames().filter { it != "time" }.zip(colors).forEach { (col, clr) ->
//            line {
//                y(col<Double>())
//                width = 1.0
//                color = clr
//            }
//        }
//    }.save("effective_load_mean.html", path = chartsPath.absolutePathString())
//}
//
//private fun removeHeaderFromCsvFile(file: Path, startingChar: Char = '#'): Path {
//    val newLines = file.readLines().filter { line -> !line.startsWith(startingChar) }
//    return Files.write(
//        file.toAbsolutePath(),
//        newLines,
//        StandardOpenOption.WRITE,
//        StandardOpenOption.TRUNCATE_EXISTING,
//    )
//}
