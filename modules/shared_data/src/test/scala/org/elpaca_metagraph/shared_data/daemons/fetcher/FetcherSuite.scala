package org.elpaca_metagraph.shared_data.daemons.fetcher

import cats.effect.IO
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.types.Refined.ApiUrl
import io.constellationnetwork.schema.address.Address
import eu.timepit.refined.auto._
import org.http4s.client.Client
import org.http4s.{HttpRoutes, Response, Uri}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait FetcherSuite {
  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromClass(this.getClass)

  protected val baseUrl: ApiUrl = ApiUrl.unsafeFrom("http://localhost:8080")
  protected val dagAddress1: Address = Address("DAG56BtU1j5uCMb5f1QxZ5oxfBhpUeYucRGygfEa")
  protected val dagAddress2: Address = Address("DAG45ZLcgmQeRHY3oV2ZJACrFUjEZwqeXKSfZc75")
  protected val dagAddress3: Address = Address("DAG4J3i4K87evc71ti3aEcSKx8AUR5GhPy3EysiM")

  def mockClient(responses: Map[Uri, Response[IO]]): Client[IO] = Client.fromHttpApp(HttpRoutes.of[IO] {
    case req if responses.contains(req.uri) =>
      responses(req.uri).pure[IO]
  }.orNotFound)

}
