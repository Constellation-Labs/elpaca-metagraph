package org.elpaca_metagraph.shared_data.daemons.fetcher

import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate

trait Fetcher[F[_]] {
  def getAddressesAndBuildUpdates: F[List[ElpacaUpdate]]
}
