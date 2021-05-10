package io.scalac.mesmer.extension.metric
import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model._

object PersistenceMetricsMonitor {

  final case class Labels(node: Option[Node], path: Path, persistenceId: PersistenceId) extends LabelSerializable {
    val serialize: RawLabels = node.serialize ++ path.serialize ++ persistenceId.serialize
  }

  trait BoundMonitor extends Bound {
    def recoveryTime: MetricRecorder[Long]
    def recoveryTotal: Counter[Long]
    def persistentEvent: MetricRecorder[Long]
    def persistentEventTotal: Counter[Long]
    def snapshot: Counter[Long]
  }

}