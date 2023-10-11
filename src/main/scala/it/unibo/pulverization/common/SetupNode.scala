package it.unibo.pulverization.common

import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class SetupNode extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport {

  override def main() = {
//    val isThickHostList = node.get[List[ID]]("thickHostsList")
//    val isThickHost = isThickHostList.contains(mid())
//    node.put("isThickHost", isThickHost)
    val neighbours = alchemistEnvironment.getNeighborhood(node.asInstanceOf[SimpleNodeManager[Any]].node).size()
    node.put("isThickHost", neighbours > 2)
    val isThickHost = node.get[Boolean]("isThickHost")
    val loads = node.get[List[ID]]("loads")
    val load = loads(mid())
    // node.put("capacity", if (isThickHost) 100 - capacity else capacity)
    if (isThickHost) { node.put("load", load) } else { node.put("consumption", randomGen.nextInt(90)) }
  }
}
