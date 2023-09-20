package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class MoveOnMostConnected extends AggregateProgram with StandardSensors with ScafiAlchemistSupport with FieldUtils with BlockG with BlockS {
  override def main() = {
    node.put("id", mid())
    val isThickHostList = node.get[List[Int]]("isThickHostList")
    val isThickHost = isThickHostList.contains(mid())
    node.put("isThickHost", isThickHost)

    val myCandidateNeighbours = foldhoodPlus(0)(_ + _) {
      mux(isThickHost) { nbr { 1 } } { nbr { 0 } }
    }

    node.put("[DEBUG] myCandidateNeighbours", excludingSelf.reifyField(myCandidateNeighbours))

    val (candidateId, size) = includingSelf.reifyField(nbr(myCandidateNeighbours))
      .maxBy { case (id, size) => (size, id) }

    node.put("[DEBUG] size", size)

    node.put("candidateId", candidateId == mid())

    candidateId

    // ------------------------------

//    val (candidateId, _) = includingSelf.reifyField(nbr(myCandidateNeighbours))
//      .view.mapValues(_.filter(e => e))
//      .map { case (id, hostKind) => id -> hostKind.size }
//      .maxBy { case (id, size) => (size, id) }
//    candidateId

//    branch(isThinHost) { Option.empty[ID] } {
//      val myNeighbours = foldhoodPlus(Set.empty[ID])(_ ++ _) {
//        nbr(Set(mid()))
//      }
//      val (candidateId, _) = includingSelf.reifyField(nbr(myNeighbours.size))
//        .maxBy { case (id, size) => (size, id) }
//
//      if (node.get("id") == candidateId) {
//        node.put("candidateId", candidateId)
//      }
//
//      Option(candidateId)
//    }
  }
}
