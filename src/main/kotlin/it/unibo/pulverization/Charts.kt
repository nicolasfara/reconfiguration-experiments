package it.unibo.pulverization

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.drop
import org.jetbrains.kotlinx.dataframe.api.dropNaNs
import org.jetbrains.kotlinx.dataframe.io.ColType
import org.jetbrains.kotlinx.dataframe.io.read
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.jetbrains.kotlinx.kandy.dsl.invoke
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layout
import org.jetbrains.kotlinx.kandy.letsplot.util.linetype.LineType
import org.jetbrains.kotlinx.kandy.util.color.Color
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.random.Random

@OptIn(ExperimentalPathApi::class)
fun main() {
    val dataPath = Path("data")
    val chartsPath = Path(dataPath.absolutePathString(), "/charts")

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
    val dataframes = dataPath.walk()
        .filter { it.name.endsWith(".csv") }
        .map {
            DataFrame.readCSV(
                it.toFile(),
                delimiter = ' ',
                header = header,
                colTypes = colTypes,
            )
        }

    dataframes.forEach { df ->
        val data = df["time", "canOffload[sum]"].dropNaNs(whereAllNaN = true)
        data.plot {
            line {
                width = 0.3
                color = Color.BLACK
                type = LineType.SOLID

                x("time"<String>())
                y("canOffload[sum]"<Int>())
            }
            layout {
                title = "Offloading"
                size = 2000 to 1000
            }
        }.save(chartsPath.absolutePathString() + "-${Random.nextInt()}.html")
    }
}
