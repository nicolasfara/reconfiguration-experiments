package it.unibo.pulverization.devices

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ThickDevice extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  private lazy val maxSystemLoad = 25
  private lazy val frame = 60
  private lazy val initialLoad = randomGen.nextDouble() * maxSystemLoad
  private lazy val randomLoadStrategy = (_: Double) => randomGen.nextDouble() * maxSystemLoad
  private lazy val sinNoisedLoadStrategy = (time: Double) => {
    val sinValue = Math.sin(time / 600) // A period of 600 time units (6 cycles per hour)
    val noise = randomGen.nextGaussian() * 15 // 15 is the standard deviation
    (60 * (sinValue + 1) / 2) + noise
  }

  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    if (isThickHost) {
      val currentTime = alchemistEnvironment.getSimulation.getTime.toDouble
      val nextLoad = nextLoadValue(initialLoad, frame, currentTime)(sinNoisedLoadStrategy)
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
