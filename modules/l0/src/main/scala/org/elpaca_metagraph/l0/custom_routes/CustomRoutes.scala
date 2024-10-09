package org.elpaca_metagraph.l0.custom_routes

import cats.effect.Async
import cats.syntax.all._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.Utils.getCurrentEpochProgress
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.States.DataSourceType._
import org.elpaca_metagraph.shared_data.types.States.{ElpacaCalculatedState, StreakDataSource}
import org.elpaca_metagraph.shared_data.types.codecs.NonNegLongCodec._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.CORS
import org.http4s.{HttpRoutes, Response}
import org.tessellation.currency.dataApplication.L0NodeContext
import org.tessellation.ext.http4s.AddressVar
import org.tessellation.routes.internal.{InternalUrlPrefix, PublicRoutes}
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

case class CustomRoutes[F[_] : Async](
  calculatedStateService: CalculatedStateService[F]
)(
  implicit context: L0NodeContext[F]
) extends Http4sDsl[F] with PublicRoutes[F] {

  @derive(encoder, decoder)
  case class CalculatedStateResponse(
    ordinal        : Long,
    calculatedState: ElpacaCalculatedState
  )

  private def getLatestCalculatedState: F[Response[F]] = {
    calculatedStateService
      .get
      .flatMap(state => Ok(CalculatedStateResponse(state.ordinal.value.value, state.state)))
  }

  private def getCalculatedStateByDataSource(dataSourceName: String): F[Response[F]] = {
    val dataSourceMap = Map(
      "exolix" -> Exolix,
      "simplex" -> Simplex,
      "integrationnetqueue" -> IntegrationnetNodeOperator,
      "walletsholdingdag" -> WalletCreationHoldingDAG,
      "freshwallets" -> FreshWallet,
      "existingwallets" -> ExistingWallets,
      "inflowtransactions" -> InflowTransactions,
      "outflowtransactions" -> OutflowTransactions,
      "x" -> X,
      "streak" -> Streak
    )

    calculatedStateService
      .get
      .flatMap { state =>
        dataSourceMap
          .get(dataSourceName.toLowerCase)
          .map(state.state.dataSources.get)
          .map(Ok(_))
          .getOrElse(NotFound())
      }
  }

  private def getAddressStreakInformation(address: Address): F[Response[F]] = {
    @derive(encoder, decoder)
    case class AddressStreakInformation(
      currentStreak         : NonNegLong,
      totalEarned           : Amount,
      claimAmount           : Amount,
      lastClaimEpochProgress: EpochProgress,
      currentEpochProgress  : EpochProgress
    )

    for {
      epochProgress <- getCurrentEpochProgress
      response <- calculatedStateService
        .get
        .flatMap { state =>
          state.state.dataSources
            .get(Streak)
            .collect { case ds: StreakDataSource => ds }
            .getOrElse(StreakDataSource(Map.empty))
            .existingWallets
            .get(address).map { addressInfo =>
              Ok(
                AddressStreakInformation(
                  addressInfo.streakDays,
                  addressInfo.totalEarned,
                  addressInfo.nextClaimReward,
                  addressInfo.epochProgressToReward,
                  epochProgress
                )
              )
            }
            .getOrElse(NotFound())
        }
    } yield response

  }

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "calculated-state" => getLatestCalculatedState
    case GET -> Root / "calculated-state" / dataSourceName => getCalculatedStateByDataSource(dataSourceName)
    case GET -> Root / "streak" / AddressVar(address) => getAddressStreakInformation(address)
  }

  val public: HttpRoutes[F] =
    CORS
      .policy
      .withAllowCredentials(false)
      .httpRoutes(routes)

  override protected def prefixPath: InternalUrlPrefix = "/"
}
