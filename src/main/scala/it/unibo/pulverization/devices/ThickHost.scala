package it.unibo.pulverization.devices

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ThickHost extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  private lazy val skewnessCoefficient = (randomGen.nextDouble() % (1.1 - 0.9)) + 0.9
  private lazy val simulationTime = node.get[Int]("simulationTime")

  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val loadType = node.get[Int]("loadType")
    if (isThickHost) {
      val currentTime = alchemistEnvironment.getSimulation.getTime.toDouble
      val nextLoad = if (loadType == 0) constantLoad() else discreteLoadStrategy(currentTime)
      node.put("load", nextLoad)
    }
  }

  private def constantLoad(): Double = 75

  private def discreteLoadStrategy(time: Double): Double = {
    val load = time match {
      case t if t < simulationTime / 5 => 80
      case t if t < simulationTime / 5 * 2 => 40
      case t if t < simulationTime / 5 * 3 => 60
      case t if t < simulationTime / 5 * 4 => 20
      case t if t < simulationTime => 30
      case _ => 0
    }
    load * skewnessCoefficient
  }
}
