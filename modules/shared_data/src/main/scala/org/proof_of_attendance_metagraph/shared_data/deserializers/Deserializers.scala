package org.proof_of_attendance_metagraph.shared_data.deserializers

import io.circe.Decoder
import io.circe.jawn.decode
import org.proof_of_attendance_metagraph.shared_data.types.DataUpdates.ProofOfAttendanceUpdate
import org.proof_of_attendance_metagraph.shared_data.types.States.{ProofOfAttendanceCalculatedState, ProofOfAttendanceOnChainState}
import org.tessellation.currency.dataApplication.DataUpdate
import org.tessellation.currency.dataApplication.dataApplication.DataApplicationBlock
import org.tessellation.security.signature.Signed

import java.nio.charset.StandardCharsets

object Deserializers {

  private def deserialize[A: Decoder](
    bytes: Array[Byte]
  ): Either[Throwable, A] =
    decode[A](new String(bytes, StandardCharsets.UTF_8))

  def deserializeUpdate(
    bytes: Array[Byte]
  ): Either[Throwable, ProofOfAttendanceUpdate] =
    deserialize[ProofOfAttendanceUpdate](bytes)

  def deserializeState(
    bytes: Array[Byte]
  ): Either[Throwable, ProofOfAttendanceOnChainState] =
    deserialize[ProofOfAttendanceOnChainState](bytes)

  def deserializeBlock(
    bytes: Array[Byte]
  )(implicit e: Decoder[DataUpdate]): Either[Throwable, Signed[DataApplicationBlock]] =
    deserialize[Signed[DataApplicationBlock]](bytes)

  def deserializeCalculatedState(
    bytes: Array[Byte]
  ): Either[Throwable, ProofOfAttendanceCalculatedState] =
    deserialize[ProofOfAttendanceCalculatedState](bytes)
}