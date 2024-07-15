package org.elpaca_metagraph.shared_data.types

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, HCursor, Json}
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress

object WalletCreationHoldingDAG {
  @derive(encoder, decoder)
  case class WalletCreationHoldingDAGDataSourceAddress(
    epochProgressToReward  : Option[EpochProgress],
    amountToReward         : Amount,
    registeredEpochProgress: EpochProgress,
    balance                : Amount
  )

  case class WalletCreationHoldingDAGApiResponse(balances: Map[Address, Amount])

  implicit val walletCreationHoldingDAGApiResponseDecoder: Decoder[WalletCreationHoldingDAGApiResponse] = (c: HCursor) => {
    for {
      // Extracting all balances fields from the JSON array
      balancesList <- c.as[List[Json]]
      balances = balancesList.flatMap { json =>
        json.hcursor.downField("balances").as[Map[Address, Amount]].toOption
      }.reduceOption(_ ++ _).getOrElse(Map.empty)
    } yield WalletCreationHoldingDAGApiResponse(balances)
  }
}
