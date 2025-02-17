package org.elpaca_metagraph.shared_data

import cats.effect.Async
import cats.syntax.all._
import io.circe.Error
import io.circe.parser.decode
import monocle.Monocle.toAppliedFocusOps
import org.elpaca_metagraph.shared_data.types.States.DataSourceType.Streak
import org.elpaca_metagraph.shared_data.types.States.{DataSource, DataSourceType, StreakDataSource}
import org.elpaca_metagraph.shared_data.types.Streak.StreakDataSourceAddress
import org.tessellation.schema.address.Address
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.io.Source

object StreakHotfix {
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("StreakHotfix")

  private def readJsonFile[F[_] : Async](fileName: String): F[Either[Error, Map[Address, StreakDataSourceAddress]]] = Async[F].delay {
    Option(getClass.getClassLoader.getResourceAsStream(fileName)) match {
      case Some(resource) =>
        val jsonString = Source.fromInputStream(resource).mkString
        decode[Map[Address, StreakDataSourceAddress]](jsonString) // This returns Either[Error, Map[Address, StreakDataSourceAddress]]
      case None =>
        Left(new io.circe.ParsingFailure(s"File $fileName not found in resources", null))
    }
  }

  def updateToCorrectState[F[_] : Async](
    oldState: Map[DataSourceType, DataSource]
  ): F[Map[DataSourceType, DataSource]] = {
    readJsonFile[F]("streak_17_02_2025.json").flatMap {
      case Right(correctState) =>
        val streakDatasource = oldState
          .get(Streak)
          .collect { case ds: StreakDataSource => ds }
          .getOrElse(StreakDataSource(Map.empty))

        val stateUpdated = correctState.foldLeft(streakDatasource.existingWallets) { (acc, curr) =>
          val (address, correctState) = curr
          acc.get(address) match {
            case Some(currentValue) =>
              acc.updated(
                address,
                currentValue.copy(
                  streakDays = correctState.streakDays,
                  nextClaimReward = correctState.nextClaimReward
                )
              )
            case None => acc.updated(
              address,
              correctState
            )
          }
        }

        val updatedStreakDataSource = streakDatasource.focus(_.existingWallets).replace(stateUpdated)

        logger[F].info("Updating streak to correct state").as(
          oldState.updated(Streak, updatedStreakDataSource)
        )


      case Left(error) =>
        logger[F].error(s"Error when updating the streak to correct state: ${error}").as(
          oldState
        )
    }
  }
}