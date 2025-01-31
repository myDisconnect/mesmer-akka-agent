package io.scalac.mesmer.core.support

//import io.scalac.mesmer.core.model.Module
import io.scalac.mesmer.core.model.SupportedVersion
import io.scalac.mesmer.core.model.SupportedVersion._
import io.scalac.mesmer.core.module._

trait ModulesSupport {
  def akkaActor: SupportedVersion
  def akkaHttp: SupportedVersion
  def akkaStream: SupportedVersion
  def akkaPersistenceTyped: SupportedVersion
  def akkaClusterTyped: SupportedVersion
}

object ModulesSupport extends ModulesSupport {

  val modules: Set[Module] = Set(
    AkkaHttpModule,
    AkkaActorModule,
    AkkaPersistenceModule,
    AkkaStreamModule,
    AkkaClusterModule
  )

  private val commonAkkaSupportedVersion: SupportedVersion =
    majors("2").and(minors("6")).and(patches("8", "9", "10", "11", "12", "13", "14"))

  val akkaActor: SupportedVersion = commonAkkaSupportedVersion

  val akkaHttp: SupportedVersion =
    majors("10")
      .and(minors("1").and(patches("8")).or(minors("2").and(patches("0", "1", "2", "3", "4"))))

  val akkaPersistenceTyped: SupportedVersion =
    commonAkkaSupportedVersion

  val akkaClusterTyped: SupportedVersion =
    commonAkkaSupportedVersion

  def akkaStream: SupportedVersion = majors("2").and(minors("6")).and(patches("8"))

}
