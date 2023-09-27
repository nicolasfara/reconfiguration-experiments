package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class LeaderElection extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with BlockG with BlockC with BlockS {

  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val leader = S(isThickHost, 4, nbrRange _)
    val leaderId = origG[ID](leader, if (leader) mid() else -1, identity, nbrRange _)
//    node.put("leader", leaderId)
//    node.put("leaderEffect", leaderId % 10)
    node.put("isLeader", leader)
    node.put("leaderId", leaderId)
    node.put("leaderEffect", leaderId)
    // Return value of the program
    leader
  }

  private def S(suitable: Boolean, grain: Double, metric: Metric): Boolean = {
    val uid = mux(suitable) { randomUid } { (Double.PositiveInfinity, Int.MaxValue) }
    breakUsingUids(suitable, uid, grain, metric) && suitable
  }

  private def breakUsingUids(suitable: Boolean, uid: (Double, Int), grain: Double, metric: Metric): Boolean = {
    uid == rep(uid) { lead: (Double, ID) =>
      val initVal = mux(suitable) { 0.0 } { Double.PositiveInfinity }
      // Distance from current device (uid) to the current leader (lead).
      val gradientSource = (uid == lead) && suitable
      val dist = origG[Double](gradientSource, initVal, (_: Double) + metric(), metric) // classicGradient(uid == lead)

      // Initially, current device is candidate, so the distance ('dist')
      // will be 0; the same will be for other devices.
      // To solve the conflict, devices abdicate in favor of devices with
      // lowest UID, according to 'distanceCompetition'.
      distanceCompetition(suitable, dist, lead, uid, grain, metric)
    }
  }

  private def distanceCompetition(suitable: Boolean, d: Double, lead: (Double, Int), uid: (Double, Int), grain: Double, metric: Metric): (Double, Int) = {
    val inf: (Double, ID) = (Double.PositiveInfinity, uid._2)
    mux(d > grain) {
      // If the current device has a distance to the current candidate leader
      //   which is > grain, then the device candidate itself for another region.
      // Remember: 'grain' represents, in the algorithm,
      //   the mean distance between two leaders.
      uid
    } {
      mux(d >= (0.5 * grain)) {
        // If the current device is at an intermediate distance to the
        //   candidate leader, then it abdicates (by returning 'inf').
        inf
      } {
        // Otherwise, elect the leader with lowest UID.
        // Note: it works because Tuple2 has an OrderingFoldable where
        //   the min(t1,t2) is defined according the 1st element, or
        //   according to the 2nd elem in case of breakeven on the first one.
        //   (minHood uses min to select the candidate leader tuple)
        minHood {
          mux(nbr(d) + metric() >= 0.5 * grain) { nbr(inf) } { nbr(lead) }
        }
      }
    }
  }

  private def origG[V](source: Boolean, field: V, acc: V => V, metric: () => Double): V =
    rep((Double.MaxValue, field)) { case (dist, value) =>
      mux(source) { (0.0, field) } {
        excludingSelf.minHoodSelector(toMinimize = nbr { dist } + metric())(data = (nbr { dist } + metric(), acc(nbr { value })))
          .getOrElse((Double.PositiveInfinity, field))
      }
    }._2
}
