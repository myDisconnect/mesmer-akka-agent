package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.Actor
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.util.ActorRefOps

object ActorCellSendMessageMetricInstrumentation {

  @OnMethodEnter
  def onEnter(@Argument(0) envelope: Object): Unit =
    if (envelope != null) {
      val sender = EnvelopeOps.getSender(envelope)
      if (sender != Actor.noSender)
        for {
          cell    <- ActorRefOps.Local.cell(sender)
          metrics <- ActorCellDecorator.get(cell) if metrics.sentMessages.isDefined
        } metrics.sentMessages.get.inc()
    }
}

object ActorCellSendMessageTimestampInstrumentation {
  @OnMethodEnter
  def onEnter(@Argument(0) envelope: Object): Unit =
    if (envelope != null) {
      EnvelopeDecorator.setTimestamp(envelope)
    }
}
