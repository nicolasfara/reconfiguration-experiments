package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Builtins._

class SimpleLoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockC
    with CustomSpawn {

  /** The baseline representing a "static" scenario in which each device has only a one device to offload the
    * computation. The device in which offload the computation is based on the closest device.
    */
  override def main(): Any = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationalCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)

    val potential = classicGradient(isThickHost)
    val leaderId = G[ID](isThickHost, mid(), identity, nbrRange _)

    val devicesWantOffloading =
      collect[Set[(ID, Double)]](potential, _ ++ _, Set((mid(), computationalCost)), Set.empty, nbrRange _)

    var accumulator = load
    val devicesCanOffloading = devicesWantOffloading.toList
      .sortBy(_._2)
      .takeWhile { case (_, cost) =>
        val condition = accumulator + cost <= 100.0
        accumulator = accumulator + cost
        condition
      }

    val offloadingLoad = devicesCanOffloading.map(_._2).sum

    val devicesCanOffload = G[List[ID]](isThickHost, devicesCanOffloading.map(_._1), identity, nbrRange _)
    val canOffload = devicesCanOffload.contains(mid())

    val latency = classicGradient(isThickHost)

    // METRICS ---------------------------------------------------------------------------------------------------------
    if (!isThickHost) { node.put("canOffload", canOffload) }
    if (!isThickHost) { node.put("wantToOffload", true) }
    node.put("latency", if (canOffload && !isThickHost) latency else Double.NaN)
    if (isThickHost) { node.put("effectiveLoad", load + offloadingLoad) }
    // -----------------------------------------------------------------------------------------------------------------

    // GRAPHICAL EFFECTS -----------------------------------------------------------------------------------------------
    node.put("leaderID", if (canOffload) leaderId else -1)
    node.put("leaderEffect", leaderId % 10)
    // -----------------------------------------------------------------------------------------------------------------
  }

  private def collect[V](potential: Double, acc: (V, V) => V, local: V, Null: V, metric: Metric): V =
    rep(local) { v =>
      acc(local, foldhood(Null)(acc)(mux(nbr(findParentFix(potential, metric)) == mid())(nbr(v))(nbr(Null))))
    }

  private def findParentFix(potential: Double, metric: Metric): ID = {
    val (minPotential, truePotential, devIdWithMinPotential) = excludingSelf
      .reifyField((nbr(potential) + metric(), nbr(potential), nbr(mid())))
      .values
      .minOption
      .getOrElse((Double.PositiveInfinity, Double.PositiveInfinity, implicitly[Bounded[ID]].top))

    mux(smaller(truePotential, minPotential))(devIdWithMinPotential)(implicitly[Bounded[ID]].top)
  }

  private def smaller[V: Bounded](a: V, b: V): Boolean = implicitly[Bounded[V]].compare(a, b) < 0
}
