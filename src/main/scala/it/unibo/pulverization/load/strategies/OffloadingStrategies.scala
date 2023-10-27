package it.unibo.pulverization.load.strategies

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{ID, ScafiAlchemistSupport}

object OffloadingStrategies {
  def randomDecisionChoice(devices: Set[(Double, ID)], load: Double)(env: ScafiAlchemistSupport): Set[(Double, ID)] = {
    val randomData = env.randomGen.shuffle(devices.toList)
    val (_, candidateDevices) = randomData
      .foldLeft((load, List.empty[(Double, ID)])) { case ((loadAcc, devicesAcc), (deviceLoad, id)) =>
        if (loadAcc + deviceLoad <= 100.0) (loadAcc + deviceLoad, (deviceLoad, id) :: devicesAcc)
        else (loadAcc, devicesAcc)
      }
    candidateDevices.toSet
  }

  def lowLoadDeviceDecisionChoice(devices: Set[(Double, ID)], load: Double): Set[(Double, ID)] = {
    val (_, candidateDevices) = devices.toList
      .sortBy { case (deviceLoad, _) => deviceLoad }
      .foldLeft((load, List.empty[(Double, ID)])) { case ((loadAcc, devicesAcc), (deviceLoad, id)) =>
        if (loadAcc + deviceLoad <= 100.0) (loadAcc + deviceLoad, (deviceLoad, id) :: devicesAcc)
        else (loadAcc, devicesAcc)
      }
    candidateDevices.toSet
  }

  def highLoadDeviceDecisionChoice(devices: Set[(Double, ID)], load: Double): Set[(Double, ID)] = {
    val (_, candidateDevices) = devices.toList
      .sortBy { case (deviceLoad, _) => deviceLoad }(Ordering[Double].reverse)
      .foldLeft((load, List.empty[(Double, ID)])) { case ((acc, list), (deviceLoad, id)) =>
        if (acc + deviceLoad <= 100.0) (acc + deviceLoad, (deviceLoad, id) :: list) else (acc, list)
      }
    candidateDevices.toSet
  }
}
