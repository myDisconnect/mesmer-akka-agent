package io.scalac.agent.akka.actor

import akka.actor.typed.Behavior

import net.bytebuddy.ByteBuddy
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.Timestamp

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
          val advice = Advice.to(classOf[ClassicStashInstrumentation])
          builder
            .visit(
              advice.on(
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
          val advice = Advice.to(classOf[TypedStashInstrumentation])
          builder
            .visit(
              advice.on(
                named[MethodDescription]("stash")
                  .or(named[MethodDescription]("clear"))
                  .or(
                    named[MethodDescription]("unstash")
                    // since there're two `unstash` methods, we need to specify parameter types
                      .and(takesArguments(classOf[Behavior[_]], classOf[Int], classOf[Function1[_, _]]))
                  )
              )
            )
//            .method(
//              named[MethodDescription]("stash")
//                .or(named[MethodDescription]("clear"))
//                .or(
//                  named[MethodDescription]("unstash")
//                  // since there're two `unstash` methods, we need to specify parameter types
//                    .and(takesArguments(classOf[Behavior[_]], classOf[Int], classOf[Function1[_, _]]))
//                )
//            )
//            .intercept(advice)
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
            .method(
              named[MethodDescription]("sendMessage").and(takesArgument(0, Class.forName("akka.dispatch.Envelope")))
            )
            .intercept(Advice.to(classOf[ActorCellSendMessageInstrumentation]))
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
            .method(named[MethodDescription]("dequeue"))
            .intercept(Advice.to(classOf[MailboxDequeueInstrumentation]))
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
    mailboxTimeDequeueInstrumentation
  )

}
