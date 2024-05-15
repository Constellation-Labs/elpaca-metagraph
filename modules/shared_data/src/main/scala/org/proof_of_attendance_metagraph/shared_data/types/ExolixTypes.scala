package org.proof_of_attendance_metagraph.shared_data.types
import io.circe.generic.auto._

object ExolixTypes {
  case class CoinInfo(
    coinCode: String,
    coinName: String,
  )

  case class ExolixTransaction(
    id               : String,
    amount           : Double,
    coinFrom         : CoinInfo,
    coinTo           : CoinInfo,
    status           : String,
    createdAt        : String,
    depositAddress   : String,
    withdrawalAddress: String
  )

  case class ExolixApiResponse(data: List[ExolixTransaction])
}
