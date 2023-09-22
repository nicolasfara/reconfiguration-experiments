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

    val grain: Double = 1.0 // mean distance between two leaders
    val gradientMetric: Metric = () => nbrRange() //   |--> Are both a metric returning a field?
    val metricDistance: Metric = () => nbrRange() //   /

    val leader = selectLeader(isThickHost, grain, nbrRange _)
    node.put("leader", leader)

    val leaderId = Gcurried[ID](leader)(if (leader) { mid() } else { -1 })(identity)(nbrRange _)
    node.put("leaderId", leaderId)
    node.put("leaderEffect", leaderId % 7)
//    // Select potential leader
//    val leader = branch(isThickHost) { S(grain, nbrRange _) } { false }
//    // Get the distance from leader
//    val distanceFromLeader = distanceTo(leader, gradientMetric)
//
//    // Get the IDs belonging to the region
//    val devicesIds = C[Double, Set[ID]](distanceFromLeader, _ ++ _, Set(mid()), Set.empty)
//
//    // Propagate the leader ID to the nodes belonging to the region
//    val leaderId = Gcurried[ID](leader)(if (leader) { mid() } else { -1 })(identity)(nbrRange _)
//
//    node.put("isThickHost", isThickHost)
//    node.put("leader", leader)
//    node.put("leaderId", leaderId)
//    node.put("leaderEffect", leaderId % 7)
//    node.put("distanceFromLeader", distanceFromLeader)
//    node.put("devicesIds", devicesIds)
  }

  private def selectLeader(isThickHost: => Boolean, grain: Double, metric: Metric): Boolean = {
    breakUsingUids(isThickHost, randomUid, grain, metric) && isThickHost
  }

  def breakUsingUids(condition: => Boolean, uid: (Double, ID),
                     grain: Double,
                     metric: Metric): Boolean =
  // Initially, each device is a candidate leader, competing for leadership.
    uid == rep(uid) { lead: (Double, ID) =>
      // Distance from current device (uid) to the current leader (lead).
      val dist = G[Double](uid == lead, 0, (_: Double) + metric(), metric)

      // Initially, current device is candidate, so the distance ('dist')
      // will be 0; the same will be for other devices.
      // To solve the conflict, devices abdicate in favor of devices with
      // lowest UID, according to 'distanceCompetition'.
      distanceCompetition(condition, dist, lead, uid, grain, metric)
    }

  def distanceCompetition(condition: => Boolean,
                           d: Double,
                          lead: (Double, ID),
                          uid: (Double, ID),
                          grain: Double,
                          metric: Metric): (Double, ID) = {
    val inf: (Double, ID) = (Double.PositiveInfinity, uid._2)
    mux(d > grain) {
      // If the current device has a distance to the current candidate leader
      //   which is > grain, then the device candidate itself for another region.
      // Remember: 'grain' represents, in the algorithm,
      //   the mean distance between two leaders.
      uid
    } {
      mux(d >= (0.5 * grain) && condition) {
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
          mux(nbr { d } + metric() >= 0.5 * grain) { nbr { inf } } { nbr { lead } }
        }
      }
    }
  }
}
