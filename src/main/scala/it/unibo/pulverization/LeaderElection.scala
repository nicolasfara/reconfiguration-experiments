package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Ordering.Implicits._

class LeaderElection extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with BlockG with BlockC with BlockS with CustomSpawn with FieldUtils with ExplicitFields {

  type Ctype = String
  type CID = Int
  type AllocationMap = Map[(Ctype, CID), ID]
  type RelocationStrategy = Set[(Ctype, CID)] => AllocationMap

  override def main(): Unit = {
//    val allocation = reconfigurationStrategy(3.0, nbrRange _){ _ => Map.empty }
//    node.put("allocation", allocation)
    val nbrCount = foldhood(0)(_ + _) { nbr(1) }
    val leaderId = multiLeader(mid(), mid(), 3.5, nbrRange _)
    node.put("leaderId", leaderId)
    node.put("isLeader", leaderId == mid())
    node.put("leaderEffect", leaderId % 10)
  }

  def reconfigurationStrategy(regionGrain: Double, metric: Metric)(strategy: RelocationStrategy): AllocationMap = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val leader = S(isThickHost, regionGrain, metric)
    val leaderId = G[ID](leader, if (leader) mid() else -1, identity, metric)
    node.put("isLeader", leader)
    node.put("leaderId", leaderId)
    node.put("leaderEffect", leaderId % 10)

    val distances = distanceTo(leader, metric)
    val gather = C[Double, Set[(Ctype, CID)]](distances, _ ++ _, Set(("", mid())), Set.empty)
    node.put("gather", gather)
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

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  def multiLeader(uid: ID, symmetryBreaker: Int, radius: Double, metric: Metric): ID = {
    val local = (-symmetryBreaker, 0.0, uid)
    val worst = (Int.MaxValue, Double.PositiveInfinity, Int.MaxValue)
    val (_, _, leaderId) = share(local) { (_, received) =>
      val msgReceived = excludingSelf.reifyField(received())
      val newDistances = excludingSelf.reifyField(metric())
      val candidacies = msgReceived
        .map { case (k, (symBrk, d, candId)) => k -> (symBrk, d + newDistances(k), candId) }
        .map { case cand @ (k, (_, d, candId)) => if (candId == uid || d >= radius) { k -> worst } else { cand } }
        .values
        .minOption
        .getOrElse(worst)

      local min candidacies
    }
    leaderId
  }
}
