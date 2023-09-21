package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ScrBasedLatency extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with BlockG with BlockC with BlockS {

  /**
   * Entrypoint of the Aggregate program.
   * @return the computation result.
   */
  override def main(): Unit = {
    // Setup simulation node
    val isThickHost = node.get[Boolean]("isThickHost")

    val grain: Double = 50.0
    val gradientMetric: Metric = () => nbrRange() //   |--> Are both a metric returning a field?
    val metricDistance: Metric = () => nbrRange() //   /

    // Select potential leader
    val leader = branch(isThickHost) { S(grain, metricDistance) } { false }
    // Get the distance from leader
    val distanceFromLeader = distanceTo(leader, gradientMetric)

    // Get the IDs belonging to the region
    val devicesIds = C[Double, Set[ID]](distanceFromLeader, _ ++ _, Set(mid()), Set.empty)

    // Propagate the leader ID to the nodes belonging to the region
    val leaderZone = Gcurried[ID](leader)(mid())(identity) { () => distanceFromLeader }

    node.put("isThickHost", isThickHost)
    node.put("leader", leader)
    node.put("leaderZone", leaderZone)
    node.put("distanceFromLeader", distanceFromLeader)
    node.put("devicesIds", devicesIds)
  }
}
