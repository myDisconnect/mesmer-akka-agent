package io.scalac.mesmer.extension.persistence

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._

import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.core.util.Timestamp

class MutableRecoveryStorageTest extends AnyFlatSpec with Matchers with TestOps {
  type Fixture = (mutable.Map[String, RecoveryStarted], MutableRecoveryStorage)
  def test(body: Fixture => Any): Any = {
    val buffer: mutable.Map[String, RecoveryStarted] = mutable.Map.empty
    val sut                                          = new MutableRecoveryStorage(buffer)
    Function.untupled(body)(buffer, sut)
  }

  "MutableRecoveryStorage" should "add started events to internal buffer" in test { case (buffer, sut) =>
    val events = List.fill(10) {
      val id = createUniqueId
      RecoveryStarted(s"/some/path/$id", id, Timestamp.create())
    }
    events.foreach(sut.recoveryStarted)
    buffer should have size events.size
    buffer.values should contain theSameElementsAs events
  }

  it should "remove started event from internal buffer when corresponding finish event is fired" in test {
    case (buffer, sut) =>
      val events = List.fill(10) {
        val id = createUniqueId
        RecoveryStarted(s"/some/path/$id", id, Timestamp.create())
      }
      events.foreach(sut.recoveryStarted)
      val finished = events
        .take(5)
        .map(started => RecoveryFinished(started.path, started.persistenceId, started.timestamp.plus(100L.millis)))
      finished.foreach(sut.recoveryFinished)

      buffer should have size (events.size - finished.size)
      buffer.values should contain theSameElementsAs events.drop(finished.size)
  }

  it should "return same storage instance with correct latency" in test { case (_, sut) =>
    val id              = createUniqueId
    val startTimestamp  = Timestamp.create()
    val path            = s"/some/path/$id"
    val expectedLatency = 1234L
    sut.recoveryStarted(RecoveryStarted(path, id, startTimestamp))
    val Some((resultStorage, latency)) =
      sut.recoveryFinished(
        RecoveryFinished(
          path,
          id,
          startTimestamp.plus(expectedLatency.millis)
        )
      )
    resultStorage should be theSameInstanceAs sut
    latency should be(expectedLatency)
  }
}
