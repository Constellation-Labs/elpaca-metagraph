package org.elpaca_metagraph.shared_data.types

import cats.Eq
import cats.syntax.all._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import org.tessellation.schema.address.Address

object ConstellationBlockExplorer {

  @derive(encoder, decoder)
  case class BlockExplorerTransaction(
    hash       : String,
    source     : Address,
    destination: Address,
    amount     : Long,
    timestamp  : String
  ) {
    implicit val eqInstance: Eq[BlockExplorerTransaction] = Eq.instance { (a, b) =>
      a.hash === b.hash
    }
  }

  case class BlockExplorerApiResponse(data: List[BlockExplorerTransaction])
}
