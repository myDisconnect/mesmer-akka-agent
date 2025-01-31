package io.scalac.mesmer.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.agent.Agent.LoadingResult

class AgentInstrumentationTest extends AnyFlatSpec with Matchers {

  behavior of "AgentInstrumentation"

  private def returning(result: LoadingResult): (Any, Any) => LoadingResult = (_, _) => result

  it should "be equal if tags and name are the same" in {
    val name = "some.class"
    val tags = Set("tag1", "tag2")

    val agent  = AgentInstrumentation(name, tags, deferred = false)(returning(LoadingResult.empty))
    val agent2 = AgentInstrumentation(name, tags, deferred = false)(returning(LoadingResult.empty))

    agent should be(agent2)
  }

  it should "be not be equal if tags are different" in {
    val name  = "some.class"
    val tags1 = Set("tag1", "tag2")
    val tags2 = Set("tag2", "tag3")

    val agent  = AgentInstrumentation(name, tags1, deferred = false)(returning(LoadingResult.empty))
    val agent2 = AgentInstrumentation(name, tags2, deferred = false)(returning(LoadingResult.empty))

    agent should not be (agent2)
  }

  it should "be not be equal if names are different" in {
    val name  = "some.class"
    val name2 = "other.class"
    val tags  = Set("tag1", "tag2")

    val agent  = AgentInstrumentation(name, tags, deferred = false)(returning(LoadingResult.empty))
    val agent2 = AgentInstrumentation(name2, tags, deferred = false)(returning(LoadingResult.empty))

    agent should not be (agent2)
  }
}
