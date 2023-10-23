package it.unibo.pulverization.load

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class IntermediateLoadBasedReconfiguration
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with BlockG
    with BlockC
    with CustomSpawn {
  override def main(): Unit = {
    val isThickHost = node.getOrElse[Boolean]("isThickHost", false)
    val computationCost = node.getOrElse[Double]("computationCost", 0.0)
    val load = node.getOrElse[Double]("load", 0.0)

    val myMetric = () => nbr(computationCost)

    val inversePotentialComputation = inverseGrad(isThickHost, load, myMetric)
    node.put("inversePotentialVal", inversePotentialComputation)

    val leaderId = Galong(isThickHost, inversePotentialComputation, mid(), identity[ID])
    val leaderIdGraphical = if (inversePotentialComputation.isFinite) leaderId else -1

    node.put("isLeader", leaderId == mid() && isThickHost)
    node.put("leaderID", leaderIdGraphical)
    node.put("leaderEffect", leaderIdGraphical % 10)
  }

  private def inverseGrad(source: => Boolean, load: Double, metric: Metric): Double =
    rep(Double.NegativeInfinity) { d =>
      val myNeighbourhood = excludingSelf.reifyField(nbr(d))
      val afterMe = myNeighbourhood.count(_._2 < d)
      val adjustment = if (afterMe == 0) 1 else afterMe
      val potential = maxHoodPlus(nbr((d - metric()) / nbr(adjustment)))
      val bounded = if (potential < metric()) Double.NegativeInfinity else potential
      mux(source)(load)(bounded)
    }

  private def Galong[V](source: Boolean, g: Double, field: V, acc: V => V): V = {
    rep(field) { case value =>
      mux(source)(field) {
        excludingSelf
          .reifyField((nbr(g), acc(nbr(value))))
          .values
          .maxByOption(_._1)
          .map(_._2)
          .getOrElse(field)
      }
    }
  }
}
