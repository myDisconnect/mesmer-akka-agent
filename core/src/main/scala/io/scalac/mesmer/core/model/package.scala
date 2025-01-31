package io.scalac.mesmer.core

import _root_.akka.actor.{ ActorPath => AkkaActorPath }
import _root_.akka.cluster.UniqueAddress
import _root_.akka.http.scaladsl.model.HttpMethod
import _root_.akka.http.scaladsl.model.Uri.{ Path => AkkaPath }

import scala.language.implicitConversions

import io.scalac.mesmer.core.model.stream.ConnectionStats
import io.scalac.mesmer.core.model.stream.StageInfo
import io.scalac.mesmer.core.tagging.@@
import io.scalac.mesmer.core.tagging._

package object model {

  type ShellInfo = (Array[StageInfo], Array[ConnectionStats])

  sealed trait ModelTag
  sealed trait NodeTag          extends ModelTag
  sealed trait InterfaceTag     extends ModelTag
  sealed trait PortTag          extends ModelTag
  sealed trait RegionTag        extends ModelTag
  sealed trait PathTag          extends ModelTag
  sealed trait StatusTag        extends ModelTag
  sealed trait MethodTag        extends ModelTag
  sealed trait PersistenceIdTag extends ModelTag
  sealed trait ActorPathTag     extends ModelTag

  type Node          = String @@ NodeTag
  type Interface     = String @@ InterfaceTag
  type Port          = Int @@ PortTag
  type Region        = String @@ RegionTag
  type Path          = String @@ PathTag
  type Status        = String @@ StatusTag
  type Method        = String @@ MethodTag
  type PersistenceId = String @@ PersistenceIdTag
  type ActorPath     = String @@ ActorPathTag
  type ActorKey      = ActorPath
  type RawLabels     = Seq[(String, String)]

  implicit val nodeLabelSerializer: LabelSerializer[Node]           = node => Seq("node" -> node.unwrap)
  implicit val interfaceLabelSerializer: LabelSerializer[Interface] = interface => Seq("interface" -> interface.unwrap)
  implicit val portLabelSerializer: LabelSerializer[Port]           = port => Seq("port" -> port.unwrap.toString)
  implicit val regionLabelSerializer: LabelSerializer[Region]       = region => Seq("region" -> region.unwrap)
  implicit val pathLabelSerializer: LabelSerializer[Path]           = path => Seq("path" -> path.unwrap)
  implicit val statusLabelSerializer: LabelSerializer[Status] = status =>
    Seq("status" -> status.unwrap, "status_group" -> s"${status.charAt(0)}xx")

  implicit val methodLabelSerializer: LabelSerializer[Method]       = method => Seq("method" -> method.unwrap)
  implicit val actorPathLabelSerializer: LabelSerializer[ActorPath] = actorPath => Seq("actor_path" -> actorPath.unwrap)
  implicit val persistenceIdLabelSerializer: LabelSerializer[PersistenceId] = persistenceId =>
    Seq("persistence_id" -> persistenceId.asInstanceOf[String])

  /**
   * This function automatically wrap string values to a tagged type but this not perform any validation - careful
   * @param value
   * @return
   */
  implicit def stringAutomaticTagger[Tag <: ModelTag](value: String): String @@ Tag = value.taggedWith[Tag]
  implicit def intAutomaticTagger[Tag <: ModelTag](value: Int): Int @@ Tag          = value.taggedWith[Tag]

  implicit class AkkaNodeOps(private val value: UniqueAddress) extends AnyVal {
    def toNode: Node = value.address.toString.taggedWith[NodeTag] // @todo change to some meaningful name
  }

  implicit class AkkaHttpPathOps(private val path: AkkaPath) extends AnyVal {
    def toPath: Path = path.toString.taggedWith[PathTag]
  }

  implicit class AkkaHttpMethodOps(private val method: HttpMethod) extends AnyVal {
    def toMethod: Method = method.value.taggedWith[MethodTag]
  }

  implicit class AkkaActorPathOps(private val path: AkkaActorPath) extends AnyVal {
    def toPath: Path           = path.toStringWithoutAddress.taggedWith[PathTag]
    def toActorPath: ActorPath = path.toStringWithoutAddress.taggedWith[ActorPathTag]
  }

  implicit class SerializationOps[T](private val tag: T) extends AnyVal {
    def serialize(implicit ls: LabelSerializer[T]): RawLabels = ls.serialize(tag)
  }

  implicit class OptionSerializationOps[T](private val optTag: Option[T]) extends AnyVal {
    def serialize(implicit ls: LabelSerializer[T]): RawLabels = optTag.fold[RawLabels](Seq.empty)(ls.serialize)
  }

}
