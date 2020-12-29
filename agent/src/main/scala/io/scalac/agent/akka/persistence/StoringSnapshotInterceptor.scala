package io.scalac.agent.akka.persistence

import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext }
import akka.persistence.SaveSnapshotSuccess
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.SnapshotCreated
import net.bytebuddy.asm.Advice._

import scala.util.Try
class StoringSnapshotInterceptor
object StoringSnapshotInterceptor {
  import AkkaPersistenceAgent.logger

  private lazy val contextField = Try {
    val context = Class
      .forName("akka.actor.typed.scaladsl.AbstractBehavior")
      .getDeclaredField("context")
    context.setAccessible(true)
    context
  }

  @OnMethodEnter
  def onSaveSnapshotResponse(
    @Argument(0) response: AnyRef,
    @This self: AbstractBehavior[_]
  ): Unit =
    contextField
      .map(_.get(self).asInstanceOf[ActorContext[_]])
      .fold(
        ex => logger.error("Couldn't find field context", ex),
        context =>
          response match {
            case SaveSnapshotSuccess(meta) => {
              context.log.trace("Snapshot for {} created", meta.persistenceId)
              EventBus(context.system)
                .publishEvent(
                  SnapshotCreated(
                    context.self.path.toString,
                    meta.persistenceId,
                    meta.sequenceNr,
                    System.currentTimeMillis()
                  )
                )
            }
            case _ =>
          }
      )

}