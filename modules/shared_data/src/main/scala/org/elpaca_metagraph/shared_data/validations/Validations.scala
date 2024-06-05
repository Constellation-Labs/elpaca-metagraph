package org.elpaca_metagraph.shared_data.validations

import org.elpaca_metagraph.shared_data.types.DataUpdates.IntegrationnetNodeOperatorUpdate
import org.elpaca_metagraph.shared_data.validations.TypeValidators.validateIfIntegrationnetOperatorHave250KDAG
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr

object Validations {
  def integrationnetNodeOperatorsValidationsL1(
    integrationnetNodeOpUpdate: IntegrationnetNodeOperatorUpdate
  ): DataApplicationValidationErrorOr[Unit] =
    validateIfIntegrationnetOperatorHave250KDAG(integrationnetNodeOpUpdate)
}
