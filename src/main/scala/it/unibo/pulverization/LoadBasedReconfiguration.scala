package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class LoadBasedReconfiguration
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
  override def main() = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)

    val myMetric = () => computationCost

    val potential = Grad(isThickHost, load, myMetric)
    val devicesCovered = C[Double, Set[(Double, ID)]](potential, _ ++ _, Set((computationCost, mid())), Set.empty)

    node.put("[potential]", potential)
    node.put("[free]", 100.0 - load)
    node.put("[devicesCovered]", devicesCovered.size)

//    val (leaderId, potential) =
//      G[(ID, Double)](isThickHost, (mid(), load), elem => (elem._1, elem._2 + computationCost), myMetric)

//    val devices = C[Double, Set[(Double, ID)]](potential, _ ++ _, Set((computationCost, mid())), Set.empty)
//

    val candidateDevices = deviceDecisionChoice(devicesCovered, 100.0 - load)
    val newAdditionalLoad: Double = candidateDevices.toList.map(_._1).sum

    node.put("deviceCovered", devicesCovered)
    node.put("candidateDevices", candidateDevices.size)
    node.put("effectiveLoad", load + newAdditionalLoad)

    val (leaderId, offloadableDevices) =
      G[(ID, Set[(Double, ID)])](isThickHost, (mid(), candidateDevices), identity, myMetric)
    val canOffload = offloadableDevices.exists { case (_, id) => id == mid() }

    node.put("offloadableDevices", offloadableDevices)

    node.put("isLeader", isThickHost)
    node.put("leaderID", if (canOffload) leaderId else -1)
    node.put("leaderEffect", leaderId % 10)

    // val potentialComputation = myGrad(isThickHost, 100 - load, myMetric)
//    val inversePotentialComputation = inverseGrad(isLeader, load, myMetric)
//    // node.put("potentialVal", potentialComputation)
//    node.put("inversePotentialVal", inversePotentialComputation)
//
//    val leaderId = Galong(isLeader, inversePotentialComputation, mid(), identity[ID])
//    val leaderIdGraphical = if (inversePotentialComputation.isFinite) leaderId else -1
  }

  private def deviceDecisionChoice(devices: Set[(Double, ID)], load: Double): Set[(Double, ID)] = {
    var accumulator = 0.0
    devices.toList
      .sortBy(_._1)
      .takeWhile { case (deviceLoad, _) =>
        val cond = accumulator + deviceLoad <= load
        accumulator = accumulator + deviceLoad
        cond
      }
      .toSet
  }

//  def bestCandidateSelection[V: Ordering](
//      candidate: Boolean,
//      value: V
//  ): (ID, Boolean) = {
//    val (id, (isCandidate, _)) = includingSelf
//      .reifyField(nbr((candidate, value)))
//      .filter { case (_, (isThick, _)) => isThick }
//      .maxByOption { case (id, (_, value)) => (value, -id) }
//      .getOrElse((mid(), (candidate, value)))
//    (id, isCandidate)
//  }

//  def myGrad(source: => Boolean, free: Double, metric: Metric): Double =
//    rep(Double.PositiveInfinity) { d =>
//      mux(source)(free)(minHoodPlus(nbr(d) + metric()))
//    }

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

  def Grad(source: Boolean, leaderValue: Double, metric: () => Double): Double = {
    share(Double.PositiveInfinity) { (_, e) =>
      val value = e()
      mux(source)(leaderValue)(minHoodPlus(nbr(value) + metric()))
    }
  }

  def grad(source: => Boolean, load: Double, metric: Metric): Double =
    rep(Double.PositiveInfinity) { d =>
      val myNeighbourhood = excludingSelf.reifyField(nbr(d))
      val afterMe = myNeighbourhood.count(_._2 > d)
      val adjustment = if (afterMe == 0) 1 else afterMe
      val potential = minHoodPlus(nbr((d + metric()) / nbr(adjustment)))
      val bounded = if (potential > metric()) Double.PositiveInfinity else potential
      mux(source)(load)(bounded)
    }

  def inverseGrad(source: => Boolean, load: Double, metric: Metric): Double =
    rep(Double.NegativeInfinity) { d =>
      val myNeighbourhood = excludingSelf.reifyField(nbr(d))
      val afterMe = myNeighbourhood.count(_._2 < d)
      val adjustment = if (afterMe == 0) 1 else afterMe
      val potential = maxHoodPlus(nbr((d - metric()) / nbr(adjustment)))
      val bounded = if (potential < metric()) Double.NegativeInfinity else potential
      mux(source)(load)(bounded)
    }

  def Galong[V](source: Boolean, g: Double, field: V, acc: V => V): V = {
    rep(field) { case value =>
      mux(source)(field) {
        excludingSelf
          .reifyField((nbr(g), acc(nbr(value))))
          .values
          .maxByOption(_._1)
          .map(_._2)
          .getOrElse(field)
      }
    }
  }
}
