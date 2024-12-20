package org.elpaca_metagraph.shared_data.validations

import cats.syntax.all._
import org.tessellation.currency.dataApplication.DataApplicationValidationError
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Errors {
  private type DataApplicationValidationType = DataApplicationValidationErrorOr[Unit]

  val valid: DataApplicationValidationType = ().validNec[DataApplicationValidationError]

  implicit class DataApplicationValidationTypeOps[E <: DataApplicationValidationError](err: E) {
    def invalid: DataApplicationValidationType = err.invalidNec[Unit]

    def unlessA(cond: Boolean): DataApplicationValidationType = if (cond) valid else invalid

    def whenA(cond: Boolean): DataApplicationValidationType = if (cond) invalid else valid
  }

  case object IntegrationnetNodeOperatorBalanceLessThan250K extends DataApplicationValidationError {
    val message = "Integrationnet node operator should have more than 250K DAG"
  }
  case object StreakUpdateNotSignedByStargazer extends DataApplicationValidationError {
    val message = "Streak update not signed by Stargazer"
  }
  case object StreakAddressAlreadyRewarded extends DataApplicationValidationError {
    val message = "Streak address already rewarded"
  }
  case object MissingToken extends DataApplicationValidationError {
    val message = "Missing token to claim reward"
  }
  case object InvalidToken extends DataApplicationValidationError {
    val message = "Invalid token"
  }
}
