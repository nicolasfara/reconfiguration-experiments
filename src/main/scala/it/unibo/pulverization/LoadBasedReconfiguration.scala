package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class LoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockS
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
    val consumptionCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)

    val neighbourConsumptionCost = foldhoodPlus(0.0)(_ + _)(nbr(consumptionCost))
    val neighboursCount = foldhoodPlus(0)(_ + _)(nbr(1))

    val myMetric = () => mux(isThickHost)(nbr(load))(nbr(consumptionCost))

    val potentialComputation = classicGradient(isThickHost, myMetric)

    // Take the Thick Host with the highest number of Thick Hosts in its neighborhood,
    // in case of tie, take the Thick Host with the lowest ID. Central node.
    val nbrCapacity = foldhoodPlus(0.0)(_ + _)(nbr(consumption))

    val (id, candidate) = bestCandidateSelection(isThickHost, nbrCapacity)
    val isLeader = isThickHost // id == mid() && candidate

    val loadMetric = () => nbr(consumption)

    // classicGradient(isLeader, loadMetric)
    val (leaderId, pl) = G[(ID, Double)](isLeader, (mid(), load), { case (id, l) => (id, l + consumption) }, loadMetric)

//    val after = excludingSelf.reifyField(nbr((potential, localCapacity)))
//      .filter { case (_, (pot, _)) => pot > potential }
//      .map { case (_, (_, local)) => local }
//      .sum
//
//    val (leaderId, _) = G[(ID, Double)](isLeader, (mid(), amount - nbrCapacity), { case (id, capacity) =>
//      val newCap = capacity - after
//      if (newCap > 0) (id, newCap) else (-1, 0.0)
//    }, loadMetric) // use the right metric

    node.put("PartLoad", pl)
    node.put("isLeader", leaderId == mid())
    node.put("leaderID", leaderId)
    node.put("leaderEffect", leaderId % 10)
  }

  def bestCandidateSelection[V: Ordering](
      candidate: Boolean,
      value: V
  ): (ID, Boolean) = {
    val (id, (isCandidate, _)) = includingSelf
      .reifyField(nbr((candidate, value)))
      .filter { case (_, (isThick, _)) => isThick }
      .maxByOption { case (id, (_, value)) => (value, -id) }
      .getOrElse((mid(), (candidate, value)))
    (id, isCandidate)
  }

  def myGrad(source: => Boolean, load: Double, metric: Metric): Double =
    rep(Double.PositiveInfinity) { d =>
      mux(source)(load)(minHoodPlus(nbr(d) + metric()))
    }
}
