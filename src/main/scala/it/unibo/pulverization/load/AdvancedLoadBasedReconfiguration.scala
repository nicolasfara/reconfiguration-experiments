package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Builtins._
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.pulverization.load.strategies.OffloadingStrategies._
import it.unibo.scafi.utils.RichStateManagement

class AdvancedLoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockC
    with CustomSpawn
    with RichStateManagement {

  private lazy val thickHosts = {
    alchemistEnvironment.getNodes
      .stream()
      .filter(node => node.getConcentration(new SimpleMolecule("isThickHost")).asInstanceOf[Boolean])
      .map(node => node.getId)
      .toList
  }

  private lazy val myIdAsThick: Int = thickHosts.indexOf(mid())

  /** Main idea of the algorithm: */
  override def main(): Unit = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)
    val deviceChoiceStrategy = node.getOrElse[String]("deviceChoiceStrategy", "highFirst")
    val interval = 400
    val active = !isThickHost ||
      (myIdAsThick + 1 > alchemistTimestamp.toDouble / interval || myIdAsThick * 2 + 1 > alchemistTimestamp.toDouble / interval)
    val isActive = if (node.get[Int]("loadType") == 0) true else active

    branch(isActive) {
      val counter = rep(0)(_ + 1)
      val myMetric = () => computationCost

      val potential = Grad(isThickHost, load, myMetric)
      val devicesCovered =
        collect[Set[(Double, ID)]](potential, _ ++ _, Set((computationCost, mid())), Set(), myMetric)

      val devicesCanOffloading = deviceChoiceStrategy match {
        case "random" => randomDecisionChoice(devicesCovered, load)(this)
        case "lowFirst" => lowLoadDeviceDecisionChoice(devicesCovered, load)
        case "highFirst" => highLoadDeviceDecisionChoice(devicesCovered, load)
        case _ => throw new IllegalStateException("Device selection strategy not handled")
      }

      val offloadingLoad: Double = devicesCanOffloading.toList.map(_._1).sum

      val (leaderId, ttl, canOffloadDevices) =
        G[(ID, Int, Set[(Double, ID)])](isThickHost, (mid(), counter, devicesCanOffloading), identity, myMetric)

      val lastN = recentValues(30, ttl)

      val isLeaderLost = if (lastN.size == 30) lastN.forall(_ == lastN.head) else false
      var canOffload = if (!isLeaderLost) canOffloadDevices.exists { case (_, id) => id == mid() }
      else false
      canOffload = canOffload && potential != Double.PositiveInfinity

      val latency = G[Double](isThickHost, 0.0, _ + nbrRange(), myMetric)

      // METRICS -------------------------------------------------------------------------------------------------------
      if (!isThickHost) {
        node.put("canOffload", canOffload)
        node.put("wantToOffload", true)
        node.put("latency", if (canOffload && !isThickHost) latency else Double.NaN)
      } else {
        node.put("effectiveLoad", load + offloadingLoad)
      }
      // ---------------------------------------------------------------------------------------------------------------

      // GRAPHICAL EFFECTS ---------------------------------------------------------------------------------------------
      node.put("isLeader", isThickHost)
      node.put("leaderID", if (canOffload || isThickHost) leaderId else -1)
      node.put("leaderEffect", leaderId % 10)
      // ---------------------------------------------------------------------------------------------------------------
    } {
      node.put("isLeader", false)
      node.put("leaderID", -1)
      node.put("leaderEffect", -1)
    }
  }

  override def G[V](source: Boolean, field: V, acc: V => V, metric: () => Double): V = {
    rep((Double.PositiveInfinity, field)) { case (dist, value) =>
      mux(source) {
        (node.get[Double]("load"), field)
      } {
        excludingSelf
          .minHoodSelector(nbr(dist) + metric())((nbr(dist) + metric(), acc(nbr(value))))
          .getOrElse((Double.PositiveInfinity, field))
      }
    }._2
  }

  private def Grad(source: Boolean, leaderValue: Double, metric: () => Double): Double = {
    rep(Double.PositiveInfinity) { e =>
      val value = e
      mux(source)(leaderValue)(minHoodPlus(nbr(value) + metric()))
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
