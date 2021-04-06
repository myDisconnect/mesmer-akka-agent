package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.core.util
import io.scalac.core.util.probe.{ Collected, ObserverCollector }
import io.scalac.extension.metric.ActorMetricMonitor._
import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver }
import io.scalac.extension.util.probe
import BoundTestProbe.MetricObserverCommand

final case class ActorMonitorTestProbe(
  mailboxSizeProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeAvgProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeMinProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeMaxProbe: TestProbe[MetricObserverCommand[Labels]],
  mailboxTimeSumProbe: TestProbe[MetricObserverCommand[Labels]],
  stashSizeProbe: TestProbe[MetricObserverCommand[Labels]],
  receivedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  processedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  failedMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeAvgProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeMinProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeMaxProbe: TestProbe[MetricObserverCommand[Labels]],
  processingTimeSumProbe: TestProbe[MetricObserverCommand[Labels]],
  sentMessagesProbe: TestProbe[MetricObserverCommand[Labels]],
  collector: ObserverCollector
)(implicit val actorSystem: ActorSystem[_])
    extends ActorMetricMonitor
    with BindUnbindMonitor
    with Collected {

  def bind() = {
    onBind()
    new ActorMonitorTestBoundMonitor with UnbindMonitor
  }

  class ActorMonitorTestBoundMonitor extends BoundMonitor {
    val mailboxSize: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(mailboxSizeProbe, collector)
    val mailboxTimeAvg: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(mailboxTimeAvgProbe, collector)
    val mailboxTimeMin: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(mailboxTimeMinProbe, collector)
    val mailboxTimeMax: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(mailboxTimeMaxProbe, collector)
    val mailboxTimeSum: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(mailboxTimeSumProbe, collector)
    val stashSize: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(stashSizeProbe, collector)
    val receivedMessages: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(receivedMessagesProbe, collector)
    val processedMessages: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(processedMessagesProbe, collector)
    val failedMessages: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(failedMessagesProbe, collector)
    val processingTimeAvg: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(processingTimeAvgProbe, collector)
    val processingTimeMin: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(processingTimeMinProbe, collector)
    val processingTimeMax: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(processingTimeMaxProbe, collector)
    val processingTimeSum: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(processingTimeSumProbe, collector)
    val sentMessages: MetricObserver[Long, Labels] =
      probe.ObserverTestProbeWrapper(sentMessagesProbe, collector)

    def unbind(): Unit = {
      collector.finish(mailboxSizeProbe)
      collector.finish(mailboxTimeAvgProbe)
      collector.finish(mailboxTimeMinProbe)
      collector.finish(mailboxTimeMaxProbe)
      collector.finish(mailboxTimeSumProbe)
      collector.finish(receivedMessagesProbe)
      collector.finish(processedMessagesProbe)
      collector.finish(failedMessagesProbe)
      collector.finish(processingTimeAvgProbe)
      collector.finish(processingTimeMinProbe)
      collector.finish(processingTimeMaxProbe)
      collector.finish(processingTimeSumProbe)
      collector.finish(sentMessagesProbe)
    }
  }
}

object ActorMonitorTestProbe {

  def apply(collector: ObserverCollector)(implicit actorSystem: ActorSystem[_]): ActorMonitorTestProbe =
    ActorMonitorTestProbe(
      TestProbe("mailbox-size-probe"),
      TestProbe("mailbox-time-avg-probe"),
      TestProbe("mailbox-time-min-probe"),
      TestProbe("mailbox-time-max-probe"),
      TestProbe("mailbox-time-sum-probe"),
      TestProbe("stash-size-probe"),
      TestProbe("received-messages-probe"),
      TestProbe("processed-messages-probe"),
      TestProbe("failed-messages-probe"),
      TestProbe("processing-time-avg-probe"),
      TestProbe("processing-time-min-probe"),
      TestProbe("processing-time-max-probe"),
      TestProbe("processing-time-sum-probe"),
      TestProbe("sent-messages-probe"),
      collector
    )

}
