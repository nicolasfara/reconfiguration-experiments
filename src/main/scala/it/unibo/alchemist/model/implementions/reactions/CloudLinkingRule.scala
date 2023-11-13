package it.unibo.alchemist.model.implementions.reactions

import it.unibo.alchemist.model._
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.neighborhoods.Neighborhoods

class CloudLinkingRule[T, P <: Position[P]] extends LinkingRule[T, P] {

  private val isThickHost = new SimpleMolecule("isThickHost")

  override def computeNeighborhood(center: Node[T], environment: Environment[T, P]): Neighborhood[T] = {
    val other = environment.getNodes
      .stream()
      .filter(node => node.getConcentration(isThickHost).asInstanceOf[Boolean])
      .filter(node => node != center)
      .toList
    Neighborhoods.make(environment, center, other)
  }

  override def isLocallyConsistent: Boolean = true
}
