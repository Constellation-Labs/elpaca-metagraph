package org.proof_of_attendance_metagraph.data_l1.daemons

import cats.effect.Async
import cats.effect.std.Supervisor
import cats.implicits.catsSyntaxFlatMapOps
import com.comcast.ip4s.{Host, Port}
import fs2.io.net.Network
import org.proof_of_attendance_metagraph.data_l1.daemons.fetcher.{ExolixFetcher, IntegrationnetNodesOperatorsFetcher, SimplexFetcher, TwitterFetcher}
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig
import org.tessellation.json.JsonSerializer
import org.tessellation.node.shared.domain.Daemon
import org.tessellation.node.shared.resources.MkHttpClient
import org.tessellation.schema.peer.P2PContext
import org.tessellation.security.SecurityProvider
import org.tessellation.security.key.ops.PublicKeyOps
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.security.KeyPair
import scala.concurrent.duration.FiniteDuration

trait DaemonApis[F[_]] {
  def spawnDaemons: F[Unit]
}

object DaemonApis {
  def make[F[_] : Async : SecurityProvider : Supervisor : Network : JsonSerializer](
    config : ApplicationConfig,
    keypair: KeyPair
  ): DaemonApis[F] = new DaemonApis[F] {

    private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(DaemonApis.getClass)

    override def spawnDaemons: F[Unit] = {
      val httpClient = MkHttpClient.forAsync[F].newEmber(config.http4s.client)
      val selfTarget = P2PContext(Host.fromString("127.0.0.1").get, Port.fromInt(9400).get, keypair.getPublic.toId.toPeerId)

      httpClient.use { client =>
        val publisher = Publisher.make[F](client, selfTarget)
        val signer = Signer.make[F](keypair, publisher)
        spawnExolixDaemon(config, signer) >>
          spawnSimplexDaemon(config, signer) >>
          spawnTwitterDaemon(config, signer) >>
          spawnIntegrationnetNodesOperatorsDaemon(config, signer)
      }
    }

    private def spawn(
      processor: Processor[F],
      idleTime : FiniteDuration
    ): Daemon[F] =
      Daemon
        .periodic[F](
          processor.execute,
          idleTime
        )

    private def spawnExolixDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val exolixFetcher = ExolixFetcher.make[F](config.exolixDaemon)
      val exolixProcessor = Processor.make[F](exolixFetcher, signer)
      logger.info("Spawning Exolix daemon") >>
        spawn(
          exolixProcessor,
          config.exolixDaemon.idleTime
        ).start
    }

    private def spawnSimplexDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val simplexFetcher = SimplexFetcher.make[F](config.simplexDaemon)
      val simplexProcessor = Processor.make[F](simplexFetcher, signer)
      logger.info("Spawning Simplex daemon") >>
        spawn(
          simplexProcessor,
          config.simplexDaemon.idleTime
        ).start
    }

    private def spawnTwitterDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val twitterFetcher = TwitterFetcher.make[F](config.twitterDaemon)
      val twitterProcessor = Processor.make[F](twitterFetcher, signer)

      logger.info("Spawning Twitter daemon") >>
        spawn(
          twitterProcessor,
          config.twitterDaemon.idleTime
        ).start
    }

    private def spawnIntegrationnetNodesOperatorsDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val integrationnetNodesOperatorsFetcher = IntegrationnetNodesOperatorsFetcher.make[F](config.integrationnetNodesOperatorsDaemon)
      val integrationnetNodesOperatorsProcessor = Processor.make[F](integrationnetNodesOperatorsFetcher, signer)

      logger.info("Spawning IntegrationnetNodeOperators daemon") >>
        spawn(
          integrationnetNodesOperatorsProcessor,
          config.integrationnetNodesOperatorsDaemon.idleTime
        ).start
    }
  }
}