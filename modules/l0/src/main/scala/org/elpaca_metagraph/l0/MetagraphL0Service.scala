package org.elpaca_metagraph.l0

import cats.data.NonEmptyList
import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined.refineV
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import org.elpaca_metagraph.l0.custom_routes.CustomRoutes
import org.elpaca_metagraph.shared_data.LifecycleSharedFunctions
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.types.DataUpdates._
import org.elpaca_metagraph.shared_data.types.States._
import org.elpaca_metagraph.shared_data.types.codecs.DataUpdateCodec._
import org.elpaca_metagraph.shared_data.validations.Errors.valid
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.{EntityDecoder, HttpRoutes}
import org.tessellation.currency.dataApplication._
import org.tessellation.currency.dataApplication.dataApplication.{DataApplicationBlock, DataApplicationValidationErrorOr}
import org.tessellation.json.JsonSerializer
import org.tessellation.schema.SnapshotOrdinal
import org.tessellation.schema.address.{Address, DAGAddressRefined}
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import scala.io.Source

object MetagraphL0Service {

  def make[F[+_] : Async : JsonSerializer](
    calculatedStateService: CalculatedStateService[F]
  ): F[BaseDataApplicationL0Service[F]] = Async[F].delay {
    makeBaseDataApplicationL0Service(
      calculatedStateService
    )
  }


  private def makeBaseDataApplicationL0Service[F[+_] : Async : JsonSerializer](
    calculatedStateService: CalculatedStateService[F]
  ): BaseDataApplicationL0Service[F] =
    BaseDataApplicationL0Service(
      new DataApplicationL0Service[F, ElpacaUpdate, ElpacaOnChainState, ElpacaCalculatedState] {
        def readLinesFromFile(fileName: String): List[String] = {
          val source = Source.fromResource(fileName)
          try {
            source.getLines().toList
          } finally {
            source.close()
          }
        }

        override def genesis: DataState[ElpacaOnChainState, ElpacaCalculatedState] = {
          val rewardedAddressesAsString = readLinesFromFile("initial_rewarded_wallets.txt")
          val rewardedAddresses = rewardedAddressesAsString.flatMap { addressAsString =>
            refineV[DAGAddressRefined](addressAsString).toOption.map(Address(_))
          }.toSet

          DataState(
            ElpacaOnChainState(List.empty),
            ElpacaCalculatedState(Map(
              DataSourceType.Exolix -> ExolixDataSource(Map.empty),
              DataSourceType.Simplex -> SimplexDataSource(Map.empty),
              DataSourceType.IntegrationnetNodeOperator -> IntegrationnetNodeOperatorDataSource(Map.empty),
              DataSourceType.WalletCreation -> WalletCreationDataSource(Map.empty, rewardedAddresses)
            ))
          )
        }

        override def validateUpdate(
          update: ElpacaUpdate
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          valid.pure

        override def validateData(
          state  : DataState[ElpacaOnChainState, ElpacaCalculatedState],
          updates: NonEmptyList[Signed[ElpacaUpdate]]
        )(implicit context: L0NodeContext[F]): F[DataApplicationValidationErrorOr[Unit]] =
          valid.pure

        override def combine(
          state  : DataState[ElpacaOnChainState, ElpacaCalculatedState],
          updates: List[Signed[ElpacaUpdate]]
        )(implicit context: L0NodeContext[F]): F[DataState[ElpacaOnChainState, ElpacaCalculatedState]] =
          LifecycleSharedFunctions.combine[F](
            state,
            updates
          )

        override def dataEncoder: Encoder[ElpacaUpdate] =
          implicitly[Encoder[ElpacaUpdate]]

        override def calculatedStateEncoder: Encoder[ElpacaCalculatedState] =
          implicitly[Encoder[ElpacaCalculatedState]]

        override def dataDecoder: Decoder[ElpacaUpdate] =
          implicitly[Decoder[ElpacaUpdate]]

        override def calculatedStateDecoder: Decoder[ElpacaCalculatedState] =
          implicitly[Decoder[ElpacaCalculatedState]]

        override def signedDataEntityDecoder: EntityDecoder[F, Signed[ElpacaUpdate]] =
          circeEntityDecoder

        override def serializeBlock(
          block: Signed[DataApplicationBlock]
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[Signed[DataApplicationBlock]](block)

        override def deserializeBlock(
          bytes: Array[Byte]
        ): F[Either[Throwable, Signed[DataApplicationBlock]]] =
          JsonSerializer[F].deserialize[Signed[DataApplicationBlock]](bytes)

        override def serializeState(
          state: ElpacaOnChainState
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[ElpacaOnChainState](state)

        override def deserializeState(
          bytes: Array[Byte]
        ): F[Either[Throwable, ElpacaOnChainState]] =
          JsonSerializer[F].deserialize[ElpacaOnChainState](bytes)

        override def serializeUpdate(
          update: ElpacaUpdate
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[ElpacaUpdate](update)

        override def deserializeUpdate(
          bytes: Array[Byte]
        ): F[Either[Throwable, ElpacaUpdate]] =
          JsonSerializer[F].deserialize[ElpacaUpdate](bytes)

        override def getCalculatedState(implicit context: L0NodeContext[F]): F[(SnapshotOrdinal, ElpacaCalculatedState)] =
          calculatedStateService.get.map(calculatedState => (calculatedState.ordinal, calculatedState.state))

        override def setCalculatedState(
          ordinal: SnapshotOrdinal,
          state  : ElpacaCalculatedState
        )(implicit context: L0NodeContext[F]): F[Boolean] =
          calculatedStateService.update(ordinal, state)

        override def hashCalculatedState(
          state: ElpacaCalculatedState
        )(implicit context: L0NodeContext[F]): F[Hash] =
          calculatedStateService.hash(state)

        override def routes(implicit context: L0NodeContext[F]): HttpRoutes[F] =
          CustomRoutes[F](calculatedStateService).public

        override def serializeCalculatedState(
          state: ElpacaCalculatedState
        ): F[Array[Byte]] =
          JsonSerializer[F].serialize[ElpacaCalculatedState](state)

        override def deserializeCalculatedState(
          bytes: Array[Byte]
        ): F[Either[Throwable, ElpacaCalculatedState]] =
          JsonSerializer[F].deserialize[ElpacaCalculatedState](bytes)
      })
}
