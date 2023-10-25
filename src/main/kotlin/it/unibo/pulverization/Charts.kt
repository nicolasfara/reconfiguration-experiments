package it.unibo.pulverization

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dropNA
import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame
import org.jetbrains.kotlinx.dataframe.api.mapToColumn
import org.jetbrains.kotlinx.dataframe.io.ColType
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import org.jetbrains.kotlinx.kandy.dsl.invoke
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layout
import org.jetbrains.kotlinx.kandy.util.color.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines

fun main() {
    val dataPath = Path("data")
    val chartsPath = Path(dataPath.absolutePathString() + "/charts")
    val resultsPath = Path(dataPath.absolutePathString() + "/results")
    resultsPath.toFile().mkdirs()

    val header = listOf(
        "time", "canOffload[sum]", "wantToOffload[sum]", "effectiveLoad[mean]", "effectiveLoad[max]",
        "effectiveLoad[min]", "latency[mean]", "latency[max]", "latency[min]",
    )
    val colTypes = mapOf(
        "time" to ColType.Double,
        "canOffload[sum]" to ColType.Double,
        "wantToOffload[sum]" to ColType.Double,
        "effectiveLoad[mean]" to ColType.Double,
        "effectiveLoad[max]" to ColType.Double,
        "effectiveLoad[min]" to ColType.Double,
        "latency[mean]" to ColType.Double,
        "latency[max]" to ColType.Double,
        "latency[min]" to ColType.Double,
    )

    val dataframes = dataPath.listDirectoryEntries("*.csv")
        .map { removeHeaderFromCsvFile(it) }
        .map {
            val scenarioRegex = Regex("it.unibo.pulverization.load.(.+),+")
            val scenario = scenarioRegex.find(it.name)!!.groupValues[1]
            DataFrame.readCSV(it.toFile(), delimiter = ' ', header = header, colTypes = colTypes) to scenario
        }.toList()

    val qosDataframe = dataframes.fold(emptyDataFrame<Any>()) { acc, (df, name) ->
        val data = df["time", "canOffload[sum]", "wantToOffload[sum]"].dropNA(whereAllNA = true)
        val offloadingCol = data.mapToColumn("offloadingQoS-$name") {
            runCatching { "canOffload[sum]"<Int>() / "wantToOffload[sum]"<Int>().toDouble() }
                .getOrElse { Double.NaN }
        }
        val newDf = if (!acc.containsColumn("time")) acc.add(data["time"]) else acc
        newDf.add(offloadingCol)
    }
    val latencyDataFrame = dataframes.fold(emptyDataFrame<Any>()) { acc, (df, name) ->
        val latencyCol = df["latency[mean]"].rename("latency-$name")
        val newDf = if (!acc.containsColumn("time")) acc.add(df["time"]) else acc
        newDf.add(latencyCol)
    }.dropNA()
    val effectiveLoadMean = dataframes.fold(emptyDataFrame<Any>()) { acc, (df, name) ->
        val effectiveLoadCol = df["effectiveLoad[mean]"].rename("effectiveLoadMean-$name")
        val newDf = if (!acc.containsColumn("time")) acc.add(df["time"]) else acc
        newDf.add(effectiveLoadCol)
    }.dropNA()

    qosDataframe.writeCSV(resultsPath.absolutePathString() + "/offloading_qos.csv")
    latencyDataFrame.writeCSV(resultsPath.absolutePathString() + "/latencies.csv")
    effectiveLoadMean.writeCSV(resultsPath.absolutePathString() + "/effective_load_mean.csv")

    // Plotting ---------------------------------------------------------------------------------------------

    plot(qosDataframe) {
        layout {
            title = "Offloading QoS"
            xAxisLabel = "Time (s)"
            yAxisLabel = "Offloading QoS"
        }
        line {
            x("time"<Double>())
            y("offloadingQoS-SimpleLoadBasedReconfiguration"<Double>())
            width = 1.0
            color = Color.ORANGE
        }
        line {
            x("time"<Double>())
            y("offloadingQoS-AdvancedLoadBasedReconfiguration"<Double>())
            width = 1.25
            color = Color.LIGHT_BLUE
        }
    }.save("offloading_qos.html", path = chartsPath.absolutePathString())

    plot(latencyDataFrame) {
        layout {
            title = "Average latency of the system"
            xAxisLabel = "Time (s)"
            yAxisLabel = "Latency (ms)"
        }
        line {
            x("time"<Double>())
            y("latency-SimpleLoadBasedReconfiguration"<Double>())
            width = 1.0
            color = Color.ORANGE
        }
        line {
            x("time"<Double>())
            y("latency-AdvancedLoadBasedReconfiguration"<Double>())
            width = 1.25
            color = Color.LIGHT_BLUE
        }
    }.save("latencies.html", path = chartsPath.absolutePathString())

    plot(effectiveLoadMean) {
        layout {
            title = "Average effective load of the system"
            xAxisLabel = "Time (s)"
            yAxisLabel = "Effective load"
        }
        line {
            x("time"<Double>())
            y("effectiveLoadMean-SimpleLoadBasedReconfiguration"<Double>())
            width = 1.0
            color = Color.ORANGE
        }
        line {
            x("time"<Double>())
            y("effectiveLoadMean-AdvancedLoadBasedReconfiguration"<Double>())
            width = 1.25
            color = Color.LIGHT_BLUE
        }
    }.save("effective_load_mean.html", path = chartsPath.absolutePathString())
}

private fun removeHeaderFromCsvFile(file: Path, startingChar: Char = '#'): Path {
    val newLines = file.readLines().filter { line -> !line.startsWith(startingChar) }
    return Files.write(
        file.toAbsolutePath(),
        newLines,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}
