package it.unibo.pulverization.common

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class SetupNode extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport {

  override def main() = {
    val isThickHostList = node.get[List[ID]]("thickHostsList")
    val isThickHost = isThickHostList.contains(mid())
    node.put("isThickHost", isThickHost)
  }
}
