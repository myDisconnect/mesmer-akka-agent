package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryActorMetricsMonitor {

  final case class MetricNames(
    mailboxSize: String,
    mailboxTimeMin: String,
    mailboxTimeMax: String,
    mailboxTimeSum: String,
    mailboxTimeCount: String,
    stashedMessages: String,
    receivedMessages: String,
    processedMessages: String,
    failedMessages: String,
    processingTimeMin: String,
    processingTimeMax: String,
    processingTimeSum: String,
    processingTimeCount: String,
    sentMessages: String,
    droppedMessages: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] {

    protected val mesmerConfig: String = "mesmer.metrics.actor-metrics"

    protected def extractFromConfig(config: Config): MetricNames = {
      val mailboxSize = config
        .tryValue("mailbox-size")(_.getString)
        .getOrElse(defaultConfig.mailboxSize)

      val mailboxTimeCount = config
        .tryValue("mailbox-time-count")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeCount)

      val mailboxTimeMin = config
        .tryValue("mailbox-time-min")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeMin)

      val mailboxTimeMax = config
        .tryValue("mailbox-time-max")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeMax)

      val mailboxTimeSum = config
        .tryValue("mailbox-time-sum")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeSum)

      val stashSize = config
        .tryValue("stash-size")(_.getString)
        .getOrElse(defaultConfig.stashedMessages)

      val receivedMessages = config
        .tryValue("received-messages")(_.getString)
        .getOrElse(defaultConfig.receivedMessages)

      val processedMessages = config
        .tryValue("processed-messages")(_.getString)
        .getOrElse(defaultConfig.processedMessages)

      val failedMessages = config
        .tryValue("failed-messages")(_.getString)
        .getOrElse(defaultConfig.failedMessages)

      val processingTimeCount = config
        .tryValue("processing-time-count")(_.getString)
        .getOrElse(defaultConfig.processingTimeCount)

      val processingTimeMin = config
        .tryValue("processing-time-min")(_.getString)
        .getOrElse(defaultConfig.processingTimeMin)

      val processingTimeMax = config
        .tryValue("processing-time-max")(_.getString)
        .getOrElse(defaultConfig.processingTimeMax)

      val processingTimeSum = config
        .tryValue("processing-time-sum")(_.getString)
        .getOrElse(defaultConfig.processingTimeSum)

      val sentMessages = config
        .tryValue("sent-messages")(_.getString)
        .getOrElse(defaultConfig.sentMessages)

      val droppedMessages = config
        .tryValue("dropped-messages")(_.getString)
        .getOrElse(defaultConfig.droppedMessages)

      MetricNames(
        mailboxSize = mailboxSize,
        mailboxTimeMin = mailboxTimeMin,
        mailboxTimeMax = mailboxTimeMax,
        mailboxTimeSum = mailboxTimeSum,
        mailboxTimeCount = mailboxTimeCount,
        stashedMessages = stashSize,
        receivedMessages = receivedMessages,
        processedMessages = processedMessages,
        failedMessages = failedMessages,
        processingTimeMin = processingTimeMin,
        processingTimeMax = processingTimeMax,
        processingTimeSum = processingTimeSum,
        processingTimeCount = processingTimeCount,
        sentMessages = sentMessages,
        droppedMessages = droppedMessages
      )
    }

    val defaultConfig: MetricNames =
      MetricNames(
        "akka_actor_mailbox_size",
        "akka_actor_mailbox_time_avg",
        "akka_actor_mailbox_time_min",
        "akka_actor_mailbox_time_max",
        "akka_actor_mailbox_time_sum",
        "akka_actor_stashed_total",
        "akka_actor_received_messages_total",
        "akka_actor_processed_messages_total",
        "akka_actor_failed_messages",
        "akka_actor_processing_time_avg",
        "akka_actor_processing_time_min",
        "akka_actor_processing_time_max",
        "akka_actor_processing_time_sum",
        "akka_actor_sent_messages_total",
        "akka_actor_dropped_messages_total"
      )

  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaActorModule.All[Boolean],
    config: Config
  ): OpenTelemetryActorMetricsMonitor =
    new OpenTelemetryActorMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))

}

final class OpenTelemetryActorMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaActorModule.All[Boolean],
  metricNames: MetricNames
) extends ActorMetricsMonitor {

  private lazy val mailboxSizeObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxSize)
      .setDescription("Tracks the size of an Actor's mailbox")
  )

  private lazy val mailboxTimeCountObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeCount)
      .setDescription("Tracks the count of messages in an Actor's mailbox")
  )

  private lazy val mailboxTimeMinObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeMin)
      .setDescription("Tracks the minimum time of an message in an Actor's mailbox")
  )

  private lazy val mailboxTimeMaxObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeMax)
      .setDescription("Tracks the maximum time of an message in an Actor's mailbox")
  )

  private lazy val mailboxTimeSumObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeSum)
      .setDescription("Tracks the sum time of the messages in an Actor's mailbox")
  )

  private lazy val stashSizeCounter = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.stashedMessages)
      .setDescription("Tracks stash operations on actors")
  )

  private lazy val receivedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.receivedMessages)
      .setDescription("Tracks the sum of received messages in an Actor")
  )

  private lazy val processedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.processedMessages)
      .setDescription("Tracks the sum of processed messages in an Actor")
  )

  private lazy val failedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.failedMessages)
      .setDescription("Tracks the sum of failed messages in an Actor")
  )

  private lazy val processingTimeCountObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeCount)
      .setDescription("Tracks the amount of processed messages")
  )

  private lazy val processingTimeMinObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeMin)
      .setDescription("Tracks the miminum processing time of an message in an Actor's receive handler")
  )

  private lazy val processingTimeMaxObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeMax)
      .setDescription("Tracks the maximum processing time of an message in an Actor's receive handler")
  )

  private lazy val processingTimeSumObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeSum)
      .setDescription("Tracks the sum processing time of an message in an Actor's receive handler")
  )

  private lazy val sentMessagesObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.sentMessages)
      .setDescription("Tracks the sum of sent messages in an Actor")
  )

  private lazy val droppedMessagesObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.droppedMessages)
      .setDescription("Tracks the sum of dropped messages in an Actor")
  )

  def bind(): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor

  class OpenTelemetryBoundMonitor
      extends ActorMetricsMonitor.BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {

    val mailboxSize: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.mailboxSize) mailboxSizeObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeCount: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.mailboxTimeCount) mailboxTimeCountObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeMin: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.mailboxTimeMin) mailboxTimeMinObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeMax: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.mailboxTimeMax) mailboxTimeMaxObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeSum: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.mailboxTimeSum) mailboxTimeSumObserver.createObserver(this) else MetricObserver.noop

    val stashedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.stashedMessages) stashSizeCounter.createObserver(this) else MetricObserver.noop

    val receivedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.receivedMessages) receivedMessagesSumObserver.createObserver(this) else MetricObserver.noop

    val processedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.processedMessages) processedMessagesSumObserver.createObserver(this) else MetricObserver.noop

    val failedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.failedMessages) failedMessagesSumObserver.createObserver(this) else MetricObserver.noop

    val processingTimeCount: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.processingTimeCount) processingTimeCountObserver.createObserver(this) else MetricObserver.noop

    val processingTimeMin: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.processingTimeMin) processingTimeMinObserver.createObserver(this) else MetricObserver.noop

    val processingTimeMax: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.processingTimeMax) processingTimeMaxObserver.createObserver(this) else MetricObserver.noop

    val processingTimeSum: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.processingTimeSum) processingTimeSumObserver.createObserver(this) else MetricObserver.noop

    val sentMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.sentMessages) sentMessagesObserver.createObserver(this) else MetricObserver.noop

    val droppedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      if (moduleConfig.droppedMessages)
        droppedMessagesObserver
          .createObserver(this)
      else MetricObserver.noop
  }

}
