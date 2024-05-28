package org.proof_of_attendance_metagraph.shared_data.types


import io.circe.{Decoder, HCursor, Json}
import org.tessellation.schema.address.Address

object WalletCreationTypes {
  case class WalletCreationApiResponse(balances: Map[Address, Long])

  implicit val walletCreationApiResponseDecoder: Decoder[WalletCreationApiResponse] = (c: HCursor) => {
    for {
      // Extracting all balances fields from the JSON array
      balancesList <- c.as[List[Json]]
      balances = balancesList.flatMap { json =>
        json.hcursor.downField("balances").as[Map[Address, Long]].toOption
      }.reduceOption(_ ++ _).getOrElse(Map.empty)
    } yield WalletCreationApiResponse(balances)
  }
}
