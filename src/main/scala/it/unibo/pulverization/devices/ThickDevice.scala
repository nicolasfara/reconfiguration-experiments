package it.unibo.pulverization.devices

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ThickDevice extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  private lazy val maxSystemLoad = 25
  private lazy val frame = 60
  private lazy val initialLoad = randomGen.nextDouble() * maxSystemLoad
  private lazy val randomLoadStrategy = (_: Double) => randomGen.nextDouble() * maxSystemLoad
  private lazy val sinNoisedLoadStrategy = (time: Double) => {
    val sinValue =
      Math.sin((randomGen.nextDouble() * 100) * time % 600) // A period of 600 time units (6 cycles per hour)
    val noise = randomGen.nextGaussian() * 15 // 15 is the standard deviation
    (60 * (sinValue + 1) / 2) + noise
  }
  private lazy val skewnessCoefficient = (randomGen.nextDouble() % (1.1 - 0.9)) + 0.9
  private lazy val discreteLoadStrategy = (time: Double) => {
    val load = time match {
      case t if t < 720 => 80
      case t if t < 1440 => 40
      case t if t < 2160 => 60
      case t if t < 2880 => 20
      case t if t < 3600 => 30
      case _ => 0
    }

    load * skewnessCoefficient
  }

  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    if (isThickHost) {
      val currentTime = alchemistEnvironment.getSimulation.getTime.toDouble
      val nextLoad = nextLoadValue(initialLoad, frame, currentTime)(discreteLoadStrategy)
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
