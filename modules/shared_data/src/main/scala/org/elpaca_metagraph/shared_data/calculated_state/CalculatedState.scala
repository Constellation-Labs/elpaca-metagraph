package org.elpaca_metagraph.shared_data.calculated_state

import org.elpaca_metagraph.shared_data.types.States.ElpacaCalculatedState
import io.constellationnetwork.schema.SnapshotOrdinal

case class CalculatedState(ordinal: SnapshotOrdinal, state: ElpacaCalculatedState)

object CalculatedState {
  def empty: CalculatedState =
    CalculatedState(SnapshotOrdinal.MinValue, ElpacaCalculatedState(Map.empty))
}
