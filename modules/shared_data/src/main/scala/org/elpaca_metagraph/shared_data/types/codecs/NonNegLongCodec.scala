package org.elpaca_metagraph.shared_data.types.codecs

import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import eu.timepit.refined.types.numeric.NonNegLong
import io.circe.{Decoder, Encoder}

object NonNegLongCodec {
  implicit val nonNegLongEncoder: Encoder[NonNegLong] = Encoder[Long].contramap(_.value)
  implicit val nonNegLongDecoder: Decoder[NonNegLong] = Decoder[Long].emap(refineV[NonNegative](_))
}
