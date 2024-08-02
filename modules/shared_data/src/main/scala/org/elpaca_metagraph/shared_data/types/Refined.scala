package org.elpaca_metagraph.shared_data.types

import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.string._

object Refined {
  type ApiUrl = String Refined Uri

  object ApiUrl {
    def from(s: String): Either[String, ApiUrl] = refineV[Uri](s)

    def unsafeFrom(s: String): ApiUrl = refineV[Uri](s) match {
      case Right(url) => url
      case Left(error) => throw new IllegalArgumentException(s"Invalid URL: $error")
    }
  }
}
