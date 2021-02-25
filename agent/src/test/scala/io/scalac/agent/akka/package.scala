package io.scalac.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation

import io.scalac.agent.akka.actor.AkkaActorAgent
import io.scalac.agent.akka.http.AkkaHttpAgent
import io.scalac.agent.akka.persistence.AkkaPersistenceAgent

package object akka {

  private val instrumentation = ByteBuddyAgent.install()
  private val agentBuilder =
    new AgentBuilder.Default()
      .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
      .`with`(AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly())
  private val modules = Map(
    AkkaActorAgent.moduleName       -> AkkaActorAgent.defaultVersion,
    AkkaHttpAgent.moduleName        -> AkkaHttpAgent.defaultVersion,
    AkkaPersistenceAgent.moduleName -> AkkaPersistenceAgent.defaultVersion
  )

  private val allInstrumentations =
    AkkaPersistenceAgent.agent ++ AkkaHttpAgent.agent ++ AkkaActorAgent.agent

  lazy val installAgent: Unit = allInstrumentations.installOn(agentBuilder, instrumentation, modules)

}
