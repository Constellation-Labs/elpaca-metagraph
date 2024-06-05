package org.elpaca_metagraph.shared_data.types.codecs

import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate
import org.tessellation.currency.dataApplication.DataUpdate

object DataUpdateCodec {
  implicit val dataUpdateEncoder: Encoder[DataUpdate] = {
    case event: ElpacaUpdate => event.asJson
    case _ => Json.Null
  }

  implicit val dataUpdateDecoder: Decoder[DataUpdate] = (c: HCursor) => c.as[ElpacaUpdate]
}
