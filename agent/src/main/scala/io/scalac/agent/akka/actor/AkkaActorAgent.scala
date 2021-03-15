package io.scalac.agent.akka.actor

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.Behavior

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers._

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }

object AkkaActorAgent {

  val moduleName: Module        = ModulesSupport.akkaActorModule
  val defaultVersion: Version   = Version(2, 6, 8)
  val version: SupportedVersion = ModulesSupport.akkaActor

  private val classicStashInstrumentation = {
    val targetClassName = "akka.actor.StashSupport"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ClassicStashInstrumentation])
                .on(
                  named[MethodDescription]("stash")
                    .or(named[MethodDescription]("prepend"))
                    .or(named[MethodDescription]("unstash"))
                    .or(
                      named[MethodDescription]("unstashAll")
                        .and(takesArguments[MethodDescription](1))
                    )
                    .or(named[MethodDescription]("clearStash"))
                )
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val typedStashInstrumentation = {
    val targetClassName = "akka.actor.typed.internal.StashBufferImpl"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[TypedStashInstrumentation])
                .on(named[MethodDescription]("stash"))
            )
            .visit(
              Advice
                .to(classOf[TypedUnstashInstrumentation])
                .on(
                  named[MethodDescription]("unstash")
                    // since there're two `unstash` methods, we need to specify parameter types
                    .and(takesArguments(classOf[Behavior[_]], classOf[Int], classOf[Function1[_, _]]))
                )
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val mailboxTimeTimestampInstrumentation = {
    val targetClassName = "akka.dispatch.Envelope"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform((builder, _, _, _) => builder.defineField(EnvelopeOps.TimestampVarName, classOf[Timestamp]))
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val mailboxTimeSendMessageInstrumentation = {
    val targetClassName = "akka.actor.dungeon.Dispatch"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ActorCellSendMessageInstrumentation])
                .on(
                  named[MethodDescription]("sendMessage")
                    .and(takesArgument(0, named[TypeDescription]("akka.dispatch.Envelope")))
                )
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val mailboxTimeDequeueInstrumentation = {
    val targetClassName = "akka.dispatch.Mailbox"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[MailboxDequeueInstrumentation])
                .on(named[MethodDescription]("dequeue"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val actorCellInstrumentation = {
    val targetClassName = "akka.actor.ActorCell"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .defineField(
              ActorTimesDecorators.MailboxTime.filedName,
              classOf[ActorTimesDecorators.FieldType]
            )
            .defineField(
              ActorTimesDecorators.ProcessingTime.filedName,
              classOf[ActorTimesDecorators.FieldType]
            )
            .defineField(
              ActorCountsDecorators.Received.fieldName,
              classOf[AtomicLong]
            )
            .defineField(
              ActorCountsDecorators.Unhandled.fieldName,
              classOf[AtomicLong]
            )
            .defineField(
              ActorCountsDecorators.Failed.fieldName,
              classOf[AtomicLong]
            )
            .visit(
              Advice
                .to(classOf[ActorCellConstructorInstrumentation])
                .on(isConstructor[MethodDescription])
            )
            .method(named[MethodDescription]("receiveMessage"))
            .intercept(MethodDelegation.to(classOf[ActorCellReceiveMessageInstrumentation]))
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val actorInstrumentation = {
    val targetClassName = "akka.actor.Actor"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](targetClassName))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[ActorUnhandledInstrumentation])
                .on(named[MethodDescription]("unhandled"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val abstractSupervisionInstrumentation = {
    val targetClassName = "akka.actor.typed.internal.AbstractSupervisor"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          hasSuperType[TypeDescription](named(targetClassName))
            .and[TypeDescription](not[TypeDescription](isInterface))
        )
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[AbstractSupervisionHandleReceiveExceptionInstrumentation])
                .on(named[MethodDescription]("handleReceiveException"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  val agent = Agent(
    classicStashInstrumentation,
    typedStashInstrumentation,
    mailboxTimeTimestampInstrumentation,
    mailboxTimeSendMessageInstrumentation,
    mailboxTimeDequeueInstrumentation,
    actorCellInstrumentation,
    actorInstrumentation,
    abstractSupervisionInstrumentation
  )

}
