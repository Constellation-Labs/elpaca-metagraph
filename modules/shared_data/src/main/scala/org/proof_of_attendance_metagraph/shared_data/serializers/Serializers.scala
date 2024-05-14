package org.proof_of_attendance_metagraph.shared_data.serializers

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.proof_of_attendance_metagraph.shared_data.types.States.{ProofOfAttendanceCalculatedState, ProofOfAttendanceOnChainState}
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationBlock
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Serializers {
  private def serialize[A: Encoder](
    serializableData: A
  ): Array[Byte] =
    serializableData.asJson.deepDropNullValues.noSpaces.getBytes(StandardCharsets.UTF_8)

  def serializeUpdate(
    update: ProofOfAttendanceUpdate
  ): Array[Byte] =
    serialize[ProofOfAttendanceUpdate](update)

  def serializeState(
    state: ProofOfAttendanceOnChainState
  ): Array[Byte] =
    serialize[ProofOfAttendanceOnChainState](state)

  def serializeBlock(
    block: Signed[DataApplicationBlock]
  )(implicit e: Encoder[DataUpdate]): Array[Byte] =
    serialize[Signed[DataApplicationBlock]](block)

  def serializeCalculatedState(
    calculatedState: ProofOfAttendanceCalculatedState
  ): Array[Byte] =
    serialize[ProofOfAttendanceCalculatedState](calculatedState)
}