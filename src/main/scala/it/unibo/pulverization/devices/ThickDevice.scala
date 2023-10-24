package it.unibo.pulverization.devices

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ThickDevice extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  private lazy val maxSystemLoad = 25
  private lazy val frame = 15
  private lazy val initialLoad = randomGen.nextDouble() * maxSystemLoad
  private lazy val randomLoadStrategy = (_: Double) => randomGen.nextDouble() * maxSystemLoad

  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    if (isThickHost) {
      val currentTime = alchemistEnvironment.getSimulation.getTime.toDouble
      val nextLoad = nextLoadValue(initialLoad, frame, currentTime)(randomLoadStrategy)
      node.put("load", nextLoad)
    }
  }

  private def nextLoadValue(load: Double, timeFrame: Double, currentTime: Double)(
      nextLoadStrategy: Double => Double
  ): Double = {
    rep((currentTime, load)) { case (still, l) =>
      if (currentTime - still > timeFrame) {
        (currentTime, nextLoadStrategy(currentTime))
      } else {
        (still, l)
      }
    }._2
  }
}
