package org.elpaca_metagraph.shared_data.combiners

import cats.syntax.all._
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.Utils.{epochProgressOneDay, toTokenAmountFormat}
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.elpaca_metagraph.shared_data.types.X.{XDataSourceAddress, XRewardInfo}
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object XCombiner {
  private val firstPostOfTheDay: Long = 1L

  private def createXRewardInfo(
    currentEpochProgress: EpochProgress,
    xUpdate             : XUpdate,
    rewardAmount        : Amount
  ): XRewardInfo = {
    XRewardInfo(
      currentEpochProgress,
      currentEpochProgress,
      toTokenAmountFormat(rewardAmount),
      xUpdate.searchText,
      List(xUpdate.postId),
      firstPostOfTheDay
    )
  }

  private def getCurrentXDataSource(
    currentCalculatedState: Map[DataSourceType, DataSource]
  ): XDataSource = {
    currentCalculatedState
      .get(DataSourceType.X) match {
      case Some(xDataSource: XDataSource) => xDataSource
      case _ => XDataSource(Map.empty)
    }
  }

  def updateRewardsOlderThanOneDay(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress
  ): Map[DataSourceType, DataSource] = {
    val xDataSource = getCurrentXDataSource(currentCalculatedState)

    val updatedXDataSourceAddresses = xDataSource.existingWallets.foldLeft(xDataSource.existingWallets) { (acc, current) =>
      val (address, xDataSourceAddress) = current
      val updatedRewards = xDataSourceAddress.addressRewards.foldLeft(xDataSourceAddress.addressRewards) { (innerAcc, innerCurrent) =>
        val (searchText, rewardInfo) = innerCurrent
        if (rewardInfo.dailyEpochProgress.value.value + epochProgressOneDay <= currentEpochProgress.value.value) {
          innerAcc
            .updated(searchText, rewardInfo
              .focus(_.dailyPostsNumber).replace(0)
            )
        } else {
          innerAcc
        }
      }

      acc.updated(
        address,
        xDataSourceAddress.focus(_.addressRewards).replace(updatedRewards)
      )
    }
    currentCalculatedState
      .updated(DataSourceType.X, xDataSource.focus(_.existingWallets).replace(updatedXDataSourceAddresses))
  }

  def updateStateX(
    currentCalculatedState: Map[DataSourceType, DataSource],
    currentEpochProgress  : EpochProgress,
    xUpdate               : XUpdate,
    applicationConfig     : ApplicationConfig
  ): XDataSource = {
    val xDataSource = getCurrentXDataSource(currentCalculatedState)

    val xDataSourceAddress = xDataSource.existingWallets
      .get(xUpdate.address) match {
      case Some(xDataSourceAddress) => xDataSourceAddress
      case None => XDataSourceAddress.empty
    }

    val maybeSearchInformation = applicationConfig.xDaemon.searchInformation
      .find(_.text == xUpdate.searchText)

    maybeSearchInformation.fold(xDataSource) { searchInformation =>
      val updatedData = xDataSourceAddress.addressRewards
        .get(xUpdate.searchText)
        .map { data =>
          def isNewDay = data.epochProgressToReward.value.value + epochProgressOneDay < currentEpochProgress.value.value

          def updateXRewardInfoNewDay() = {
            data
              .focus(_.dailyEpochProgress)
              .replace(currentEpochProgress)
              .focus(_.epochProgressToReward)
              .replace(currentEpochProgress)
              .focus(_.dailyPostsNumber)
              .replace(firstPostOfTheDay)
              .focus(_.amountToReward)
              .replace(toTokenAmountFormat(searchInformation.rewardAmount))
              .focus(_.postIds)
              .replace(List(xUpdate.postId))
          }

          def isNotExceedingDailyLimit = data.dailyPostsNumber < searchInformation.maxPerDay
          def postAlreadyExists = data.postIds.contains(xUpdate.postId)

          def updateXRewardInfoSameDay() = {
            //If we receive multiple updates to the same address in the same epoch progress we need to increase the rewardAmount
            if (data.epochProgressToReward === currentEpochProgress) {
              data
                .focus(_.dailyPostsNumber)
                .modify(current => current + 1)
                .focus(_.amountToReward)
                .modify(current => current.plus(toTokenAmountFormat(searchInformation.rewardAmount)).getOrElse(current))
                .focus(_.postIds)
                .modify(current => current :+ xUpdate.postId)
            } else {
              data
                .focus(_.epochProgressToReward)
                .replace(currentEpochProgress)
                .focus(_.dailyPostsNumber)
                .modify(current => current + 1)
                .focus(_.postIds)
                .modify(current => current :+ xUpdate.postId)
            }
          }

          if (isNewDay) {
            updateXRewardInfoNewDay()
          } else if (isNotExceedingDailyLimit && !postAlreadyExists) {
            updateXRewardInfoSameDay()
          } else {
            data
          }
        }
        .getOrElse(createXRewardInfo(currentEpochProgress, xUpdate, searchInformation.rewardAmount))

      val updatedDataSourceAddress = xDataSourceAddress
        .focus(_.addressRewards)
        .modify(_.updated(xUpdate.searchText, updatedData))

      xDataSource
        .focus(_.existingWallets)
        .modify(_.updated(xUpdate.address, updatedDataSourceAddress))
    }
  }
}
