package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

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

    val myMetric = () => computationCost

    val potential = Grad(isThickHost, load, myMetric)
    val devicesCovered = C[Double, Set[(Double, ID)]](potential, _ ++ _, Set((computationCost, mid())), Set.empty)

    val candidateDevices = deviceDecisionChoice(devicesCovered, load)
    val offloadingLoad: Double = candidateDevices.toList.map(_._1).sum

    val (leaderId, canOffloadDevices) =
      G[(ID, Set[(Double, ID)])](isThickHost, (mid(), candidateDevices), identity, myMetric)
    val canOffload = canOffloadDevices.exists { case (_, id) => id == mid() }

    val latency = G[Double](isThickHost, 0.0, _ + nbrRange(), myMetric)

    // METRICS ---------------------------------------------------------------------------------------------------------
    node.put("canOffload", canOffload)
    node.put("wantToOffload", !isThickHost)
    node.put("latency", if (canOffload && !isThickHost) latency else Double.NaN)
    if (isThickHost) { node.put("effectiveLoad", load + offloadingLoad) }
    // -----------------------------------------------------------------------------------------------------------------

    // GRAPHICAL EFFECTS -----------------------------------------------------------------------------------------------
    node.put("isLeader", isThickHost)
    node.put("leaderID", if (canOffload) leaderId else -1)
    node.put("leaderEffect", leaderId % 10)
    // -----------------------------------------------------------------------------------------------------------------
  }

  private def deviceDecisionChoice(devices: Set[(Double, ID)], load: Double): Set[(Double, ID)] = {
    var accumulator = load
    devices.toList
      .sortBy(_._1)
      .takeWhile { case (deviceLoad, _) =>
        val cond = accumulator + deviceLoad <= 100.0
        accumulator = accumulator + deviceLoad
        cond
      }
      .toSet
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
}
