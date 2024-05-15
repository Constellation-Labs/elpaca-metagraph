package org.proof_of_attendance_metagraph.shared_data.types.codecs

import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.tessellation.currency.dataApplication.DataUpdate

object DataUpdateCodec {
  implicit val dataUpdateEncoder: Encoder[DataUpdate] = {
    case event: ProofOfAttendanceUpdate => event.asJson
    case _ => Json.Null
  }

  implicit val dataUpdateDecoder: Decoder[DataUpdate] = (c: HCursor) => c.as[ProofOfAttendanceUpdate]
}
