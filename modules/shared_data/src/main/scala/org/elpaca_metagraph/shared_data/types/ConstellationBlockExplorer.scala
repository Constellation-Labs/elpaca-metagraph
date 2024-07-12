package org.elpaca_metagraph.shared_data.types

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
    override def equals(obj: Any): Boolean = obj match {
      case that: BlockExplorerTransaction => this.hash == that.hash
      case _ => false
    }

    override def hashCode(): Int = hash.hashCode()
  }

  case class BlockExplorerApiResponse(data: List[BlockExplorerTransaction])
}
