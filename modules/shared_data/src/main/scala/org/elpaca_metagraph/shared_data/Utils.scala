package org.elpaca_metagraph.shared_data

import cats.data.NonEmptySet
import cats.effect.Async
import cats.effect.std.Random
import cats.syntax.all._
import eu.timepit.refined.types.all.PosLong
import eu.timepit.refined.types.numeric.NonNegLong
import org.elpaca_metagraph.shared_data.app.ApplicationConfig
import org.elpaca_metagraph.shared_data.app.ApplicationConfig.SearchInfo
import org.elpaca_metagraph.shared_data.types.Lattice.SocialDataSourceAddress
import io.constellationnetwork.currency.dataApplication.L0NodeContext
import io.constellationnetwork.env.env.{KeyAlias, Password, StorePath}
import io.constellationnetwork.ext.cats.syntax.next.catsSyntaxNext
import io.constellationnetwork.keytool.KeyStoreUtils
import io.constellationnetwork.schema.ID.Id
import io.constellationnetwork.schema.address.Address
import io.constellationnetwork.schema.balance.Amount
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.transaction.{RewardTransaction, TransactionAmount}
import io.constellationnetwork.security.SecurityProvider
import io.constellationnetwork.security.hash.Hash
import io.constellationnetwork.security.signature.signature.SignatureProof
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.security.KeyPair
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


object Utils {
  private val oneHourInSeconds = 60 * 60
  private val oneEpochProgressInSeconds = 43
  private val epochProgressesInOneHour = oneHourInSeconds / oneEpochProgressInSeconds
  private val oneDayInHours = 24

  val epochProgressOneDay: Int = 5

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

  def randomString[F[_]: Async](input: String, length: Int): F[String] = {
    val hash = Hash.fromBytes(input.getBytes("utf-8")).toString
    val seed = BigInt(hash.take(16), 16).toLong

    Random.scalaUtilRandomSeedLong[F](seed).flatMap { random =>
      (1 to length).toList.traverse(_ => random.nextAlphaNumeric).map(_.mkString)
    }
  }

  def isWithinDailyLimit(
    searchInformation: List[SearchInfo],
    wallet           : SocialDataSourceAddress
  ): Boolean = searchInformation.exists { searchInfo =>
    wallet.addressRewards
      .get(searchInfo.text.toLowerCase)
      .fold(true)(_.dailyPostsNumber < searchInfo.maxPerDay)
  }

  def timeRangeFromDayStartTillNow(
    currentDateTime: LocalDateTime
  ): (LocalDateTime, LocalDateTime) = {
    val startOfDay = currentDateTime.withHour(0).withMinute(0).withSecond(0).withNano(0)
    val endOfDay = currentDateTime.minusMinutes(1)
    (startOfDay, endOfDay)
  }

  def timeRangeFromDayStartTillNowFormatted(
    currentDateTime: LocalDateTime,
    formatter      : DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  ): (String, String) = {
    val (startOfDay, endOfDay) = timeRangeFromDayStartTillNow(currentDateTime)
    (startOfDay.format(formatter), endOfDay.format(formatter))
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