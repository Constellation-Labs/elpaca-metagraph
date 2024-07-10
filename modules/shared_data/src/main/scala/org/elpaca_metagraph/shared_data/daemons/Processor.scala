package org.elpaca_metagraph.shared_data.daemons

import cats.effect.Async
import cats.syntax.all._
import org.elpaca_metagraph.shared_data.daemons.fetcher.Fetcher
import org.elpaca_metagraph.shared_data.types.DataUpdates.ElpacaUpdate
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait Processor[F[_]] {
  def execute: F[Unit]
}

object Processor {

  def make[F[_] : Async](
    fetcher: Fetcher[F],
    signer : Signer[F]
  ): Processor[F] =
    new Processor[F] {

      private val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromClass(Processor.getClass)
      private val batch_size: Int = 100
      private val batch_interval: FiniteDuration = 10.seconds

      def execute: F[Unit] =
        process
          .flatMap { remotePending =>
            logger.info(
              s"The daemon attempted processing ${remotePending._1.length + remotePending._2.length} updates from remote. " +
                s"Successful - ${remotePending._1.length}; Failed - ${remotePending._2.length}"
            )
          }

      private def process: F[(List[ElpacaUpdate], List[ElpacaUpdate])] = for {
        _ <- logger.info(s"Starting getAddressesAndBuildUpdates with date: ${LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        updates <- fetcher.getAddressesAndBuildUpdates(LocalDateTime.now(ZoneOffset.UTC))
        subArrays = updates.sliding(batch_size, batch_size).toList.zipWithIndex

        _ <- logger.info(s"${updates.length} updates were split into ${subArrays.length} batches of $batch_size updates")

        initialAcc = (List.empty[ElpacaUpdate], List.empty[ElpacaUpdate])
        result <- subArrays.foldM(initialAcc) { case (acc, (batchUpdates, index)) =>
          for {
            _ <- logger.info(s"Processing batch ${index + 1} of ${subArrays.length}")
            results <- batchUpdates.traverse { update =>
              signer.signAndPublish(update).map { success =>
                if (success) (List(update), List.empty[ElpacaUpdate])
                else (List.empty[ElpacaUpdate], List(update))
              }
            }

            // Flattening the results to get the successes and failures
            (successes, failures) = results.unzip
            flattenedSuccesses = successes.flatten
            flattenedFailures = failures.flatten
            newAcc = (acc._1 ++ flattenedSuccesses, acc._2 ++ flattenedFailures)

            _ <- logger.info(s"Batch ${index + 1} successes: ${flattenedSuccesses.length}")
            _ <- logger.info(s"Batch ${index + 1} failures: ${flattenedFailures.length}")

            // Wait for the next batch execution
            _ <- logger.info(s"Waiting ${batch_interval.toString()} for the next batch")
            _ <- Async[F].sleep(batch_interval)
          } yield newAcc
        }
      } yield result
    }
}
