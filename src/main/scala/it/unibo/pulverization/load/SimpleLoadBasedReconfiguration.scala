package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class SimpleLoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockC
    with CustomSpawn {
  /**
   * The baseline representing a "static" scenario in which each device has only a one device to offload the computation.
   * The device in which offload the computation is based on the closest device.
   */
  override def main(): Any = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationalCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)

    val potential = classicGradient(isThickHost)
    val leaderId = G[ID](isThickHost, mid(), identity, nbrRange _)

    val devicesWantOffloading =
      C[Double, Set[(ID, Double)]](potential, _ ++ _, Set((mid(), computationalCost)), Set.empty)

    var accumulator = load
    val devicesCanOffloading = devicesWantOffloading.toList
      .sortBy(_._2)
      .takeWhile { case (_, cost) =>
        val condition = accumulator + cost <= 100.0
        accumulator = accumulator + cost
        condition
      }
      .toSet

    node.put("[DEBUG] devicesCanOffloading", devicesCanOffloading)
    node.put("[DEBUG] devicesWantOffloading", devicesWantOffloading)
    node.put("[DEBUG] potential", potential)

    val offloadingCost = devicesCanOffloading.toList.map(_._2).sum
    node.put("effectiveLoad", load + offloadingCost)

    val devicesCanOffload = G[Set[ID]](isThickHost, devicesCanOffloading.map(_._1), identity, nbrRange _)
    val canOffload = devicesCanOffload.contains(mid())

    node.put("wantToOffload", !isThickHost)
    node.put("canOffload", canOffload)

    node.put("leaderID", leaderId)
    // node.put("leaderID", if (canOffload) leaderId else -1)
    node.put("leaderEffect", leaderId % 10)
  }
}
