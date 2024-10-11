package org.elpaca_metagraph.shared_data

import cats.data.NonEmptySet
import cats.effect.Async
import cats.syntax.all._
import eu.timepit.refined.types.all.PosLong
import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.tessellation.currency.dataApplication.L0NodeContext
import org.tessellation.env.env.{KeyAlias, Password, StorePath}
import org.tessellation.ext.cats.syntax.next.catsSyntaxNext
import org.tessellation.keytool.KeyStoreUtils
import org.tessellation.schema.ID.Id
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.schema.epoch.EpochProgress
import org.tessellation.schema.transaction.{RewardTransaction, TransactionAmount}
import org.tessellation.security.SecurityProvider
import org.tessellation.security.signature.signature.SignatureProof
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.security.KeyPair
import scala.util.Random


object Utils {
  val epochProgressOneDay: Long = 60 * 24
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("Utils")

  def toTokenFormat(
    balance: Long
  ): Long = {
    (balance * 10e7).toLong
  }

  def toTokenFormat(
    amount: Amount
  ): Long = {
    toTokenFormat(amount.value.value)
  }

  def toTokenAmountFormat(
    balance: Long
  ): Amount = {
    Amount(NonNegLong.unsafeFrom(toTokenFormat(balance)))
  }

  def toTokenAmountFormat(
    amount: Amount
  ): Amount = {
    Amount(NonNegLong.unsafeFrom(toTokenFormat(amount)))
  }

  def walletSignedTheMessage(
    walletPublicKey: Id,
    proofs         : NonEmptySet[SignatureProof]
  ): Boolean =
    !proofs.toNonEmptyList.exists(
      _.id != walletPublicKey
    )

  def getCurrentEpochProgress[F[_] : Async](implicit context: L0NodeContext[F]): F[EpochProgress] = {
    context.getLastCurrencySnapshot.flatMap {
      case Some(value) => value.epochProgress.next.pure[F]
      case None =>
        val message = "Could not get the epochProgress from currency snapshot. lastCurrencySnapshot not found"
        logger.error(message) >> new Exception(message).raiseError[F, EpochProgress]
    }
  }

  def isNewDay(epochProgressToReward: EpochProgress, currentEpochProgress: EpochProgress): Boolean =
    epochProgressToReward == EpochProgress.MinValue ||
      epochProgressToReward.value.value + epochProgressOneDay < currentEpochProgress.value.value

  def loadKeyPair[F[_] : Async : SecurityProvider](config: ApplicationConfig): F[KeyPair] = {
    val keyStore = StorePath(config.nodeKey.keystore)
    val alias = KeyAlias(config.nodeKey.alias)
    val password = Password(config.nodeKey.password)

    KeyStoreUtils
      .readKeyPairFromStore[F](
        keyStore.value.toString,
        alias.value.value,
        password.value.value.toCharArray,
        password.value.value.toCharArray
      )
  }

  def loadKeyPair[F[_] : Async : SecurityProvider](
    keyStore: StorePath,
    alias   : KeyAlias,
    password: Password
  ): F[KeyPair] =
    KeyStoreUtils
      .readKeyPairFromStore[F](
        keyStore.value.toString,
        alias.value.value,
        password.value.value.toCharArray,
        password.value.value.toCharArray
      )

  def randomString(length: Int): String = {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    Random.alphanumeric.filter(chars.contains(_)).take(length).mkString
  }

  implicit class RewardTransactionOps(tuple: (Address, PosLong)) {
    def toRewardTransaction: RewardTransaction = {
      val (address, amount) = tuple
      RewardTransaction(address, TransactionAmount(amount))
    }
  }

  implicit class PosLongOps(value: Long) {
    def toPosLongUnsafe: PosLong =
      PosLong.unsafeFrom(value)
  }
}