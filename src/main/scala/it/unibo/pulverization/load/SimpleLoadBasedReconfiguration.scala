package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Builtins._
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.pulverization.load.strategies.OffloadingStrategies._
import it.unibo.scafi.utils.RichStateManagement

class SimpleLoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockC
    with CustomSpawn
    with RichStateManagement {

  private lazy val thickHosts = alchemistEnvironment.getNodes
    .stream()
    .filter(node => node.getConcentration(new SimpleMolecule("isThickHost")).asInstanceOf[Boolean])
    .map(node => node.getId)
    .toList

  private def getDegradationFactor(time: Double, maxTime: Double): Double = {
    time match {
      case t if t < maxTime / 7 => 1.0
      case t if t < (maxTime / 7) * 2 => 0.75
      case t if t < (maxTime / 7) * 3 => 0.50
      case t if t < (maxTime / 7) * 4 => 0.25
      case t if t < (maxTime / 7) * 5 => 0.50
      case t if t < (maxTime / 7) * 6 => 0.75
      case _ => 1.0
    }
  }

  override def main(): Unit = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationalCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)
    val deviceChoiceStrategy = node.getOrElse[String]("deviceChoiceStrategy", "highFirst")
    val simulationTime = node.get[Int]("simulationTime")
    val gradientRetentionTime = node.get[Int]("gradientRetentionTime")

    val nodeToTake =
      math.ceil(getDegradationFactor(alchemistTimestamp.toDouble, simulationTime.toDouble) * thickHosts.size())
    val active = thickHosts.stream().limit(nodeToTake.toInt).toList.contains(mid())

    val isActive = if (node.get[Int]("loadType") == 0) active || !isThickHost else true

    branch(isActive) {
      val counter = rep(0)(_ + 1)
      val potential = classicGradient(isThickHost)
      val leaderId = G[ID](isThickHost, mid(), identity, nbrRange _)

      val devicesCovered =
        collect[Set[(Double, ID)]](potential, _ ++ _, Set((computationalCost, mid())), Set.empty, nbrRange _)

      val devicesCanOffloading = deviceChoiceStrategy match {
        case "random" => randomDecisionChoice(devicesCovered, load)(this)
        case "lowFirst" => lowLoadDeviceDecisionChoice(devicesCovered, load)
        case "highFirst" => highLoadDeviceDecisionChoice(devicesCovered, load)
        case _ => throw new IllegalStateException("Device selection strategy not handled")
      }

      val offloadingLoad = devicesCanOffloading.toList.map(_._1).sum

      val (ttl, devicesCanOffload) =
        G[(Int, Set[ID])](isThickHost, (counter, devicesCanOffloading.map(_._2)), identity, nbrRange _)

      val lastN = recentValues(gradientRetentionTime * 2, ttl)
      val isLeaderLost = if (lastN.size == gradientRetentionTime * 2) lastN.forall(_ == lastN.head) else false

      var canOffload = if (!isLeaderLost) devicesCanOffload.contains(mid()) else false
      canOffload = canOffload && potential != Double.PositiveInfinity

      val latency = classicGradient(isThickHost)

      // METRICS ---------------------------------------------------------------------------------------------------------
      if (!isThickHost) {
        node.put("canOffload", canOffload)
        node.put("wantToOffload", true)
        node.put("latency", if (canOffload && !isThickHost) latency else Double.NaN)
      } else {
        node.put("effectiveLoad", load + offloadingLoad)
      }
      // -----------------------------------------------------------------------------------------------------------------

      // GRAPHICAL EFFECTS -----------------------------------------------------------------------------------------------
      node.put("isLeader", isThickHost)
      node.put("leaderID", if (canOffload || isThickHost) leaderId else -1)
      node.put("leaderEffect", leaderId % 10)
      // -----------------------------------------------------------------------------------------------------------------
    } {
      node.put("isLeader", false)
      node.put("leaderID", -1)
      node.put("leaderEffect", -1)
    }
  }

  private def collect[V](potential: Double, acc: (V, V) => V, local: V, Null: V, metric: Metric): V =
    rep(local) { v =>
      acc(local, foldhood(Null)(acc)(mux(nbr(findParentFix(potential, metric)) == mid())(nbr(v))(nbr(Null))))
    }

  private def findParentFix(potential: Double, metric: Metric): ID = {
    val (minPotential, truePotential, devIdWithMinPotential) = excludingSelf
      .reifyField((nbr(potential) + metric(), nbr(potential), nbr(mid())))
      .values
      .minOption
      .getOrElse((Double.PositiveInfinity, Double.PositiveInfinity, implicitly[Bounded[ID]].top))

    mux(smaller(truePotential, minPotential))(devIdWithMinPotential)(implicitly[Bounded[ID]].top)
  }

  private def smaller[V: Bounded](a: V, b: V): Boolean = implicitly[Bounded[V]].compare(a, b) < 0
}
