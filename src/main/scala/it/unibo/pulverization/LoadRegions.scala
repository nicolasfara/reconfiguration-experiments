package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class LoadRegions extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with FieldUtils with BlockG with BlockS with CustomSpawn {
  /**
   * Main idea of the algorithm:
   * 1. Each node computes the number of Thick Hosts in its neighborhood.
   * 2. If the node is a Thick Host and in its neighborhood there is at least one Thick Host,
   *   becomes a candidate leader if it has the highest numbers of Thick Hosts in its neighborhood,
   *   otherwise the Thick Host with lowest ID becomes the candidate leader.
   *   Note: this is to favorite "central" Thick Hosts.
   * 3. Each source node (Thick Host) propagates a gradient based on its actual available capacity.
   *   Idea: more the Thick Host is loaded, less the gradient is propagated.
   *   Gradient metric idea: the source node propagates a potential energy which is decreased by each other node
   *   until the potential energy is not more sufficient to cover other devices. (stability issues?)
   *   Each Thick Host has a potential energy determined by its actual load.
   *   Each Thin Device has a potential energy determined by a cost model for the components that should be moved.
   *   E.g. if a Thin Device not offloads its components, it do not pay any cost in the potential energy of the Thick Host.
   */
  override def main() = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val load = node.get[Int]("load")
    val consumption = node.getOrElse[Int]("consumption", 0) // Only on Thin devices

    // Take the Thick Host with the highest number of Thick Hosts in its neighborhood,
    // in case of tie, take the Thick Host with the lowest ID. Central node.
    val nbrCount = foldhoodPlus(0)(_ + _) { nbr(1) }
    val (id, candidate) = bestCandidateSelection(isThickHost, nbrCount)

    val metricValue = (isThickHost, candidate) match {
      case (true, false) => 0
      case (true, true) => load
      case _ => consumption
    }

    val leader = G[ID](id == mid() && candidate, mid(), identity, () => nbr(metricValue))
    node.put("isLeader", leader == mid())
    node.put("leaderID", leader)
    node.put("leaderEffect", leader % 10)
  }

  def bestCandidateSelection[V : Ordering](candidate: Boolean, value: V): (ID, Boolean) = {
    val (id, (isCandidate, _)) = includingSelf.reifyField(nbr { (candidate, value) })
      .filter { case (_, (isThick, _)) => isThick }
      .maxByOption { case (id, (_, value)) => (value, -id) }
      .getOrElse((mid(), (candidate, value)))
    (id, isCandidate)
  }
}
