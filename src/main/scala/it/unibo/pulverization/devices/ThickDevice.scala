package it.unibo.pulverization.devices

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ThickDevice extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  override def main(): Any = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val frame = 80 * randomGen.nextDouble() + 80
    val newLoad = randomGen.nextInt(100)
    val currentTime = alchemistEnvironment.getSimulation.getTime.toDouble
    rep(currentTime) { still =>
      if (currentTime - still > frame) {
        if (isThickHost) node.put("load", newLoad)
        currentTime
      } else { still }
    }
  }
}
