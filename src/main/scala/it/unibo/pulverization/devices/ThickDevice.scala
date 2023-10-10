package it.unibo.pulverization.devices

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class ThickDevice extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  override def main(): Any = {
    val frame = 80 * randomGen.nextDouble() + 80
    val newCapacity = randomGen.nextInt(100)
    val currentTime = alchemistEnvironment.getSimulation.getTime.toDouble
    rep(currentTime) { still =>
      if (currentTime - still > frame) {
        node.put("capacity", newCapacity)
        currentTime
      } else { still }
    }
  }
}
