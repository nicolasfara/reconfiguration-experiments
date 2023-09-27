package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class LeaderElection extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with BlockG with BlockC with BlockS {

  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val leader = S(isThickHost, 3, nbrRange _)
    val leaderId = G[ID](leader, if (leader) mid() else -1, identity, nbrRange _)

    node.put("isLeader", leader)
    node.put("leaderId", leaderId)
    node.put("leaderEffect", leaderId)

    leader
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
      val dist = G[Double](leaderSource, 0.0, (_: Double) + metric(), metric) // classicGradient(uid == lead)

      // Initially, current device is candidate, so the distance ('dist')
      // will be 0; the same will be for other devices.
      // To solve the conflict, devices abdicate in favor of devices with
      // lowest UID, according to 'distanceCompetition'.
      distanceCompetition(dist, lead, uid, grain, metric)
    } && suitable
  }
}
