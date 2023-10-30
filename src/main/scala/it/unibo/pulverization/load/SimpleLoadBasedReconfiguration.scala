package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Builtins._
import it.unibo.pulverization.load.strategies.OffloadingStrategies._

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
    val deviceChoiceStrategy = node.getOrElse[String]("deviceChoiceStrategy", "random")

    val potential = classicGradient(isThickHost)
    val leaderId = G[ID](isThickHost, mid(), identity, nbrRange _)

    val devicesCovered =
      collect[Set[(Double, ID)]](potential, _ ++ _, Set((computationalCost, mid())), Set.empty, nbrRange _)

    val devicesCanOffloading = deviceChoiceStrategy match {
      case "random" => randomDecisionChoice(devicesCovered, load)(this)
      case "lowFirst" => lowLoadDeviceDecisionChoice(devicesCovered, load)
      case "highFirst" => highLoadDeviceDecisionChoice(devicesCovered, load)
      case _ => throw new IllegalStateException("Device selection strategy not handled")
    }

    val offloadingLoad = devicesCanOffloading.toList.map(_._1).sum

    val devicesCanOffload = G[Set[ID]](isThickHost, devicesCanOffloading.map(_._2), identity, nbrRange _)
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
