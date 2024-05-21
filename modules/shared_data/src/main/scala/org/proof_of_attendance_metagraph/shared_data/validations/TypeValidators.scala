package org.proof_of_attendance_metagraph.shared_data.validations

import org.proof_of_attendance_metagraph.shared_data.Utils.toTokenAmountFormat
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.IntegrationnetNodeOperatorUpdate
import org.proof_of_attendance_metagraph.shared_data.validations.Errors.IntegrationnetNodeOperatorBalanceLessThan250K
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object TypeValidators {
  def validateIfIntegrationnetOperatorHave250KDAG(
    integrationnetOpUpdate: IntegrationnetNodeOperatorUpdate
  ): DataApplicationValidationErrorOr[Unit] = {
    val balance = integrationnetOpUpdate.operatorInQueue.walletBalance
    val dag_collateral = toTokenAmountFormat(250000)
    IntegrationnetNodeOperatorBalanceLessThan250K.unlessA(balance >= dag_collateral)
  }
}
