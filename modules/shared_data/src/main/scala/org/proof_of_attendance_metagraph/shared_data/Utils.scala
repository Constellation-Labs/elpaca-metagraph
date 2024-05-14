package org.proof_of_attendance_metagraph.shared_data

import cats.effect.Async
import eu.timepit.refined.types.all.PosLong
import org.proof_of_attendance_metagraph.shared_data.app.ApplicationConfig
import org.tessellation.env.env.{KeyAlias, Password, StorePath}
import org.tessellation.keytool.KeyStoreUtils
import org.tessellation.schema.address.Address
import org.tessellation.schema.transaction.{RewardTransaction, TransactionAmount}
import org.tessellation.security.SecurityProvider
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.security.KeyPair


object Utils {
  def logger[F[_] : Async]: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromName[F]("Utils")

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