package org.elpaca_metagraph.shared_data.daemons

import cats.effect.Async
import cats.effect.std.Supervisor
import cats.syntax.all._
import com.comcast.ip4s.{Host, Port}
import fs2.io.net.Network
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.calculated_state.CalculatedStateService
import org.elpaca_metagraph.shared_data.daemons.fetcher._
import org.http4s.client.Client
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
  def spawnL1Daemons: F[Unit]

  def spawnL0Daemons(calculatedStateService: CalculatedStateService[F]): F[Unit]
}

object DaemonApis {
  def make[F[_] : Async : SecurityProvider : Supervisor : Network : JsonSerializer](
    config : ApplicationConfig,
    keypair: KeyPair
  ): DaemonApis[F] = new DaemonApis[F] {
    private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(DaemonApis.getClass)

    private def withHttpClient[A](useClient: Client[F] => F[A]): F[A] = {
      val httpClient = MkHttpClient.forAsync[F].newEmber(config.http4s.client)
      httpClient.use(useClient)
    }

    private def createP2PContext(keypair: KeyPair): P2PContext =
      P2PContext(Host.fromString(config.dataApi.ip).get, Port.fromInt(config.dataApi.port).get, keypair.getPublic.toId.toPeerId)

    override def spawnL1Daemons: F[Unit] =
      withHttpClient { client =>
        val publisher = Publisher.make[F](client, createP2PContext(keypair))
        val signer = Signer.make[F](keypair, publisher)

        logger.info("Spawning L1 daemons") >>
          spawnExolixDaemon(config, signer) >>
          spawnSimplexDaemon(config, signer) >>
          spawnIntegrationnetNodesOperatorsDaemon(config, signer)
      }

    override def spawnL0Daemons(calculatedStateService: CalculatedStateService[F]): F[Unit] =
      withHttpClient { client =>
        val publisher = Publisher.make[F](client, createP2PContext(keypair))
        val signer = Signer.make[F](keypair, publisher)

        logger.info("Spawning L0 daemons") >>
          spawnWalletCreationDaemon(config, signer, calculatedStateService) >>
          spawnInflowTransactionsDaemon(config, signer, calculatedStateService) >>
          spawnOutflowTransactionsDaemon(config, signer, calculatedStateService) >>
          spawnXDaemon(config, signer, calculatedStateService)
      }

    private def spawn(
      processor: Processor[F],
      idleTime : FiniteDuration
    ): Daemon[F] =
      Daemon.periodic[F](
        processor.execute,
        idleTime
      )

    private def spawnExolixDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val exolixFetcher = ExolixFetcher.make[F](config)
      val exolixProcessor = Processor.make[F](exolixFetcher, signer)
      logger.info("Spawning Exolix daemon") >>
        spawn(exolixProcessor, config.exolixDaemon.idleTime).start
    }

    private def spawnSimplexDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val simplexFetcher = SimplexFetcher.make[F](config)
      val simplexProcessor = Processor.make[F](simplexFetcher, signer)
      logger.info("Spawning Simplex daemon") >>
        spawn(simplexProcessor, config.simplexDaemon.idleTime).start
    }

    private def spawnIntegrationnetNodesOperatorsDaemon(
      config: ApplicationConfig,
      signer: Signer[F]
    ): F[Unit] = {
      val integrationnetNodesOperatorsFetcher = IntegrationnetNodesOperatorsFetcher.make[F](config)
      val integrationnetNodesOperatorsProcessor = Processor.make[F](integrationnetNodesOperatorsFetcher, signer)
      logger.info("Spawning IntegrationnetNodeOperators daemon") >>
        spawn(integrationnetNodesOperatorsProcessor, config.integrationnetNodesOperatorsDaemon.idleTime).start
    }

    private def spawnWalletCreationDaemon(
      config                : ApplicationConfig,
      signer                : Signer[F],
      calculatedStateService: CalculatedStateService[F]
    ): F[Unit] = {
      val newWalletsFetcher = WalletCreationHoldingDAGFetcher.make[F](config, calculatedStateService)
      val newWalletsProcessor = Processor.make[F](newWalletsFetcher, signer)
      logger.info("Spawning WalletCreationHoldingDAG daemon") >>
        spawn(newWalletsProcessor, config.walletCreationHoldingDagDaemon.idleTime).start
    }

    private def spawnInflowTransactionsDaemon(
      config                : ApplicationConfig,
      signer                : Signer[F],
      calculatedStateService: CalculatedStateService[F]
    ): F[Unit] = {
      val inflowTransactionsFetcher = InflowTransactionsFetcher.make[F](config, calculatedStateService)
      val inflowTransactionsProcessor = Processor.make[F](inflowTransactionsFetcher, signer)
      logger.info("Spawning Inflow Transactions daemon") >>
        spawn(inflowTransactionsProcessor, config.inflowTransactionsDaemon.idleTime).start
    }

    private def spawnOutflowTransactionsDaemon(
      config                : ApplicationConfig,
      signer                : Signer[F],
      calculatedStateService: CalculatedStateService[F]
    ): F[Unit] = {
      val outflowTransactionsFetcher = OutflowTransactionsFetcher.make[F](config, calculatedStateService)
      val outflowTransactionsProcessor = Processor.make[F](outflowTransactionsFetcher, signer)
      logger.info("Spawning Outflow Transactions daemon") >>
        spawn(outflowTransactionsProcessor, config.inflowTransactionsDaemon.idleTime).start
    }

    private def spawnXDaemon(
      config                : ApplicationConfig,
      signer                : Signer[F],
      calculatedStateService: CalculatedStateService[F]
    ): F[Unit] = {
      val xFetcher = XFetcher.make[F](config, calculatedStateService)
      val outflowTransactionsProcessor = Processor.make[F](xFetcher, signer)
      logger.info("Spawning X daemon") >>
        spawn(outflowTransactionsProcessor, config.xDaemon.idleTime).start
    }
  }
}
