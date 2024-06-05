package org.elpaca_metagraph.shared_data.types

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
  ) {
    override def equals(obj: Any): Boolean = obj match {
      case that: ExolixTransaction => this.id == that.id
      case _ => false
    }

    override def hashCode(): Int = id.hashCode()
  }

  case class ExolixApiResponse(data: List[ExolixTransaction])
}
