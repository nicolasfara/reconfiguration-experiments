package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class LeaderElection extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with BlockG with BlockC with BlockS {

  type Ctype = String
  type CID = Int
  type AllocationMap = Map[(Ctype, CID), ID]
  type RelocationStrategy = Set[(Ctype, CID)] => AllocationMap

  override def main(): Any = {
    val allocation = reconfigurationStrategy(3.0, nbrRange _){ _ => Map.empty }
    node.put("allocation", allocation)
  }

  def reconfigurationStrategy(regionGrain: Double, metric: Metric)(strategy: RelocationStrategy): AllocationMap = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val leader = S(isThickHost, regionGrain, metric)
    val leaderId = G[ID](leader, if (leader) mid() else -1, identity, metric)
    node.put("isLeader", leader)
    node.put("leaderId", leaderId)
    node.put("leaderEffect", leaderId)

    val distances = distanceTo(leader, metric)
    val gather = C[Double, Set[(Ctype, CID)]](distances, _ ++ _, Set(("", mid())), Set.empty)
    val leaderDecision = strategy(gather)
    G[AllocationMap](leader, leaderDecision, identity, metric)
  }

  private def S(suitable: Boolean, grain: Double, metric: Metric): Boolean = {
    // Prevents oscillations on the border of the region
    val uid = mux(suitable) { randomUid } { (Double.PositiveInfinity, Int.MaxValue) }
    breakUsingUids(suitable, uid, grain, metric)
  }

  private def breakUsingUids(suitable: Boolean, uid: (Double, Int), grain: Double, metric: Metric): Boolean = {
    uid == rep(uid) { lead: (Double, ID) =>
      val leaderSource = (uid == lead) && suitable

      // Distance from current device (uid) to the current leader (lead).
      val dist = G(leaderSource, 0.0, (_: Double) + metric(), metric) // classicGradient(uid == lead)

      // Initially, current device is candidate, so the distance ('dist')
      // will be 0; the same will be for other devices.
      // To solve the conflict, devices abdicate in favor of devices with
      // lowest UID, according to 'distanceCompetition'.
      distanceCompetition(dist, lead, uid, grain, metric)
    } && suitable
  }
}
