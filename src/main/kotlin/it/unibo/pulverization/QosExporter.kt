package it.unibo.pulverization

import it.unibo.alchemist.boundary.extractors.AbstractDoubleExporter
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule

class QosExporter @JvmOverloads constructor(
    precision: Int? = null,
) : AbstractDoubleExporter(precision) {

    private val canOffloadMolecule = SimpleMolecule("canOffload")
    private val wantToOffloadMolecule = SimpleMolecule("wantToOffload")

    override val columnNames: List<String> = listOf("qos")

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long,
    ): Map<String, Double> {
        val sumCanOffload = environment.nodes.sumOf { it.getConcentration(canOffloadMolecule) as Double }
        val sumWantOffload = environment.nodes.sumOf { it.getConcentration(wantToOffloadMolecule) as Double }
        return mapOf("qos" to sumWantOffload / sumCanOffload)
    }
}
