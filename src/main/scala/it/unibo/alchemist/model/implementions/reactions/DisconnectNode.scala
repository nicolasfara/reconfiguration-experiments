package it.unibo.alchemist.model.implementions.reactions

import it.unibo.alchemist.model._
import it.unibo.alchemist.model.implementations.reactions.AbstractGlobalReaction
import it.unibo.alchemist.model.molecules.SimpleMolecule
import org.apache.commons.math3.random.RandomGenerator

class DisconnectNode[T, P <: Position[P]](
    environment: Environment[T, P],
    distribution: TimeDistribution[T],
    random: RandomGenerator
) extends AbstractGlobalReaction(environment, distribution) {
  private val isActive = new SimpleMolecule("isActive")
  private val isThickHostMolecule = new SimpleMolecule("isThickHost")
  private var inactive = 0

  override protected def executeBeforeUpdateDistribution(): Unit = {
    if (environment.getSimulation.getTime.toDouble < 3600 / 2) {
      environment.getNodes
        .stream()
        .filter(node => node.getConcentration(isThickHostMolecule).asInstanceOf[Boolean])
        .limit(inactive)
        .forEach(node => node.setConcentration(isActive, false.asInstanceOf[T]))
      inactive += 1
    } else {
      environment.getNodes
        .stream()
        .filter(node => node.getConcentration(isThickHostMolecule).asInstanceOf[Boolean])
        .limit(5 - inactive)
        .forEach(node => node.setConcentration(isActive, true.asInstanceOf[T]))
      inactive -= 1
    }
  }
}
