package io.scalac.mesmer.extension.upstream

import io.opentelemetry.api.metrics.OpenTelemetryNoopMeter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaActorSystemModule.ActorSystemModuleConfig
import io.scalac.mesmer.extension.metric.ActorSystemMonitor.Labels
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopCounter
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedCounter

class OpenTelemetryActorSystemMonitorTest extends AnyFlatSpec with Matchers {

  behavior of "OpenTelemetryActorSystemMonitor"
  val TestLabels: Labels = Labels(None)

  private def config(value: Boolean) = ActorSystemModuleConfig(
    createdActors = value,
    terminatedActors = value
  )

  it should "bind to OpenTelemetry instruments if metric is enabled" in {

    val sut = new OpenTelemetryActorSystemMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryActorSystemMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)

    bound.createdActors should be(a[WrappedCounter])
    bound.terminatedActors should be(a[WrappedCounter])
  }

  it should "bind to noop instruments if metric is disabled" in {

    val sut = new OpenTelemetryActorSystemMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryActorSystemMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)

    bound.createdActors should be(a[NoopCounter.type])
    bound.terminatedActors should be(a[NoopCounter.type])
  }
}
