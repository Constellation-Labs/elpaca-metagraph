package org.elpaca_metagraph.shared_data.daemons.fetcher

import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate

import java.time.LocalDateTime

trait Fetcher[F[_]] {
  def getAddressesAndBuildUpdates(currentDate: LocalDateTime): F[List[ElpacaUpdate]]
}
