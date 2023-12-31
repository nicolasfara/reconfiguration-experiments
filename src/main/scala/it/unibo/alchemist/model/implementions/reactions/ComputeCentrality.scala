package it.unibo.alchemist.model.implementions.reactions

import it.unibo.alchemist.model._
import it.unibo.alchemist.model.implementations.reactions.AbstractGlobalReaction
import it.unibo.alchemist.model.molecules.SimpleMolecule
import org.apache.commons.math3.random.RandomGenerator

class ComputeCentrality[T, P <: Position[P]](
    environment: Environment[T, P],
    distribution: TimeDistribution[T],
    random: RandomGenerator,
    thickHostAsLeader: Int,
    computationalCost: Double
) extends AbstractGlobalReaction(environment, distribution) {
  private val isThickHostMolecule = new SimpleMolecule("isThickHost")
  private val isActive = new SimpleMolecule("isActive")
  private val computationCostMolecule = new SimpleMolecule("computationCost")
  private val loadMolecule = new SimpleMolecule("load")
  private lazy val nodeCount = environment.getNodes.size()

  override protected def executeBeforeUpdateDistribution(): Unit = {
    setupThickDevices()
    setupComputationalCostAndLoads(computationalCost)
    setupAllActiveDevices()
  }

  override def initializationComplete(time: Time, environment: Environment[T, _]): Unit =
    getTimeDistribution.update(Time.INFINITY, true, 0.0, environment)

  private def setupThickDevices(): Unit = {
    environment.getNodes
      .stream()
      .sorted((a, b) => environment.getNeighborhood(b).size() - environment.getNeighborhood(a).size())
      .limit(Math.ceil(nodeCount * 0.05).toLong) // The leaders are the 5% of the entire network
      .forEach { node =>
        node.setConcentration(isThickHostMolecule, true.asInstanceOf[T])
        environment.moveNodeToPosition(node, environment.getPosition(node))
      }
  }

  private def setupComputationalCostAndLoads(computationalCost: Double): Unit = {
    environment.getNodes
      .stream()
      .forEach { node =>
        val isThickHost = node.getConcentration(isThickHostMolecule).asInstanceOf[Boolean]
        if (isThickHost) {
          node.setConcentration(loadMolecule, (random.nextDouble() * 100.0).asInstanceOf[T])
        } else {
          node.setConcentration(computationCostMolecule, computationalCost.asInstanceOf[T])
        }
      }
  }

  private def setupAllActiveDevices(): Unit = {
    environment.getNodes
      .stream()
      .forEach { node =>
        node.setConcentration(isActive, true.asInstanceOf[T])
      }
  }
}
