package io.scalac.extension.util.probe

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.ActorMetricMonitor.BoundMonitor
import io.scalac.extension.metric.{ ActorMetricMonitor, MetricObserver }
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe.MetricObserverCommand

class ActorMonitorTestProbe(ping: FiniteDuration)(implicit val actorSystem: ActorSystem[_]) extends ActorMetricMonitor {

  import ActorMetricMonitor._
  import ActorMonitorTestProbe._

  private val bindsMap = mutable.HashMap.empty[Labels, TestBoundMonitor]

  override def bind(labels: Labels): TestBoundMonitor = synchronized {
    bindsMap.getOrElseUpdate(labels, new TestBoundMonitor(TestProbe(), ping))
  }

}

object ActorMonitorTestProbe {
  import ActorMetricMonitor._
  class TestBoundMonitor(val mailboxSizeProbe: TestProbe[MetricObserverCommand], ping: FiniteDuration)(
    implicit actorSystem: ActorSystem[_]
  ) extends BoundMonitor
      with TestProbeSynchronized {
    override val mailboxSize: MetricObserver[Long] with CancellableTestProbeWrapper =
      ObserverTestProbeWrapper(mailboxSizeProbe, ping)
    override def unbind(): Unit =
      mailboxSize.cancel()
  }
}