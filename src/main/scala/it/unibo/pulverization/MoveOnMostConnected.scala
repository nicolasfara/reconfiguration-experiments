package it.unibo.pulverization

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class MoveOnMostConnected extends AggregateProgram
  with StandardSensors with ScafiAlchemistSupport with FieldUtils with BlockG with BlockS with CustomSpawn {
  override def main() = {
    val isThickHost = node.get[Boolean]("isThickHost")
    val capacity = node.get[Int]("capacity")
    val nbc = foldhoodPlus(0)(_ + _) { nbr(1) }
    val (id, (candidate, _)) = includingSelf.reifyField(nbr((isThickHost, nbc)))
      .filter { case (_, (isThick, _)) => isThick }
      .maxByOption { case (id, (_, nb)) => (nb, -id) }
      .getOrElse((mid(), (isThickHost, nbc)))

    val leader = myGrad[ID](id == mid() && candidate, mid(), identity, nbrRange _)
    node.put("isLeader", leader == mid())
    node.put("leaderID", leader)
    node.put("leaderEffect", leader % 10)
  }

  def myGrad[V](source: Boolean, field: V, acc: V => V, metric: Metric): V = {
    share((Double.PositiveInfinity, field)) { (_, f) =>
      val (dist, value) = f()
      mux(source) { (0.0, field) } {
        excludingSelf.minHoodSelector(nbr(dist) + metric())((nbr(dist) + metric(), acc(nbr(value))))
          .getOrElse((Double.PositiveInfinity, field))
      }
    }._2
  }
}
