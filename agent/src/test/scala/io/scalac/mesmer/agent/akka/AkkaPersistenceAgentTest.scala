package io.scalac.mesmer.agent.akka

import java.util.UUID

import _root_.akka.actor.testkit.typed.scaladsl.TestProbe
import _root_.akka.actor.typed.receptionist.Receptionist
import _root_.akka.actor.typed.receptionist.Receptionist.Deregister
import _root_.akka.actor.typed.receptionist.Receptionist.Register
import _root_.akka.util.Timeout
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.agent.utils.DummyEventSourcedActor
import io.scalac.mesmer.agent.utils.DummyEventSourcedActor.DoNothing
import io.scalac.mesmer.agent.utils.DummyEventSourcedActor.Persist
import io.scalac.mesmer.agent.utils.InstallModule
import io.scalac.mesmer.agent.utils.SafeLoadSystem
import io.scalac.mesmer.core.event.PersistenceEvent
import io.scalac.mesmer.core.event.PersistenceEvent._
import io.scalac.mesmer.core.persistenceServiceKey
import io.scalac.mesmer.core.util.ReceptionistOps

class AkkaPersistenceAgentTest
    extends InstallModule(AkkaPersistenceAgent)
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with OptionValues
    with ReceptionistOps
    with SafeLoadSystem {

  implicit val askTimeout: Timeout = Timeout(1.minute)

  type Fixture = TestProbe[PersistenceEvent]

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[PersistenceEvent]
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)
    onlyRef(monitor.ref, persistenceServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(persistenceServiceKey, monitor.ref)
  }

  "AkkaPersistenceAgent" should "generate only recovery events" in test { monitor =>
    agent.instrumentations should have size (4)
    val id = UUID.randomUUID()
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)

    val actor = system.systemActorOf(DummyEventSourcedActor(id), id.toString)

    actor ! DoNothing

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
    monitor.expectNoMessage()
  }

  it should "generate recovery, persisting and snapshot events for single persist event" in test { monitor =>
    val id = UUID.randomUUID()
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)

    val actor = system.systemActorOf(DummyEventSourcedActor(id), id.toString)

    actor ! Persist

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
    monitor.expectMessageType[PersistingEventStarted]
    monitor.expectMessageType[PersistingEventFinished]
    monitor.expectMessageType[SnapshotCreated]
  }

  it should "generate recovery, persisting and snapshot events for multiple persist events" in test { monitor =>
    val id = UUID.randomUUID()
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)
    val persistEvents = List.fill(5)(Persist)

    val actor = system.systemActorOf(DummyEventSourcedActor(id, 2), id.toString)

    persistEvents.foreach(actor.tell)

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
    for {
      seqNo <- persistEvents.indices
    } {
      monitor.expectMessageType[PersistingEventStarted]
      monitor.expectMessageType[PersistingEventFinished]
      if (seqNo % 2 == 1) {
        monitor.expectMessageType[SnapshotCreated]
      }
    }
  }

}
