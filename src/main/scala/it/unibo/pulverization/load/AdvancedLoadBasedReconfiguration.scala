package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Builtins._
import it.unibo.pulverization.load.strategies.OffloadingStrategies._

class AdvancedLoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockC
    with CustomSpawn {

  /** Main idea of the algorithm:
    *   1. Each node computes the number of Thick Hosts in its neighborhood.
    *
    * 2. If the node is a Thick Host and in its neighborhood there is at least one Thick Host, becomes a candidate
    * leader if it has the highest numbers of Thick Hosts in its neighborhood, otherwise the Thick Host with lowest ID
    * becomes the candidate leader. Note: this is to favorite "central" Thick Hosts. 3. Each source node (Thick Host)
    * propagates a gradient based on its actual available capacity. Idea: more the Thick Host is loaded, less the
    * gradient is propagated. Gradient metric idea: the source node propagates a potential energy which is decreased by
    * each other node until the potential energy is not more sufficient to cover other devices. (stability issues?) Each
    * Thick Host has a potential energy determined by its actual load. Each Thin Device has a potential energy
    * determined by a cost model for the components that should be moved. E.g. if a Thin Device not offloads its
    * components, it do not pay any cost in the potential energy of the Thick Host.
    */
  override def main(): Unit = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)
    val deviceChoiceStrategy = node.getOrElse[String]("deviceChoiceStrategy", "random")

    val myMetric = () => computationCost

    val potential = Grad(isThickHost, load, myMetric)
    val devicesCovered =
      collect[Set[(Double, ID)]](potential, _ ++ _, Set((computationCost, mid())), Set.empty, myMetric)

    val devicesCanOffloading = deviceChoiceStrategy match {
      case "random" => randomDecisionChoice(devicesCovered, load)(this)
      case "lowFirst" => lowLoadDeviceDecisionChoice(devicesCovered, load)
      case "highFirst" => highLoadDeviceDecisionChoice(devicesCovered, load)
      case _ => throw new IllegalStateException("Device selection strategy not handled")
    }

    val offloadingLoad: Double = devicesCanOffloading.toList.map(_._1).sum

    val (leaderId, canOffloadDevices) =
      G[(ID, Set[(Double, ID)])](isThickHost, (mid(), devicesCanOffloading), identity, myMetric)
    val canOffload = canOffloadDevices.exists { case (_, id) => id == mid() }

    val latency = G[Double](isThickHost, 0.0, _ + nbrRange(), myMetric)

    // METRICS ---------------------------------------------------------------------------------------------------------
    if (!isThickHost) { node.put("canOffload", canOffload) }
    if (!isThickHost) { node.put("wantToOffload", true) }
    node.put("latency", if (canOffload && !isThickHost) latency else Double.NaN)
    if (isThickHost) { node.put("effectiveLoad", load + offloadingLoad) }
    // -----------------------------------------------------------------------------------------------------------------

    // GRAPHICAL EFFECTS -----------------------------------------------------------------------------------------------
    node.put("isLeader", isThickHost)
    node.put("leaderID", if (canOffload) leaderId else -1)
    node.put("leaderEffect", leaderId % 10)
    // -----------------------------------------------------------------------------------------------------------------
  }

  override def G[V](source: Boolean, field: V, acc: V => V, metric: () => Double): V = {
    rep((Double.MaxValue, field)) { case (dist, value) =>
      mux(source) {
        (node.get[Double]("load"), field)
      } {
        excludingSelf
          .minHoodSelector(nbr(dist) + metric())((nbr(dist) + metric(), acc(nbr(value))))
          .getOrElse((Double.PositiveInfinity, field))
      }
    }._2
  }

  private def Grad(source: Boolean, leaderValue: Double, metric: () => Double): Double = {
    share(Double.PositiveInfinity) { (_, e) =>
      val value = e()
      mux(source)(leaderValue)(minHoodPlus(nbr(value) + metric()))
    }
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
