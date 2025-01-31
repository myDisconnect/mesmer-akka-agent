package io.scalac.mesmer.extension.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.concurrent.duration._

import io.scalac.mesmer.core.event.HttpEvent.RequestCompleted
import io.scalac.mesmer.core.event.HttpEvent.RequestFailed
import io.scalac.mesmer.core.event.HttpEvent.RequestStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.core.util.Timestamp

class MutableRequestStorageTest extends AnyFlatSpec with Matchers with TestOps {
  type Fixture = (mutable.Map[String, RequestStarted], MutableRequestStorage)
  def test(body: Fixture => Any): Any = {
    val buffer: mutable.Map[String, RequestStarted] = mutable.Map.empty
    val sut                                         = new MutableRequestStorage(buffer)
    Function.untupled(body)(buffer, sut)
  }

  "MutablePersistStorage" should "add started events to internal buffer" in test { case (buffer, sut) =>
    val events = List.fill(10) {
      val id = createUniqueId
      RequestStarted(id, Timestamp.create(), "/some/path", "GET")
    }
    events.foreach(sut.requestStarted)

    buffer should have size events.size
    buffer.values should contain theSameElementsAs events
  }

  it should "remove started event from internal buffer when corresponding finish event is fired" in test {
    case (buffer, sut) =>
      val startTimestamp = Timestamp.create()
      val events = List.fill(10) {
        val id = createUniqueId
        RequestStarted(id, startTimestamp, "/some/path", "GET")
      }
      events.foreach(sut.requestStarted)
      val finished = events
        .take(5)
        .map(started => RequestCompleted(started.id, startTimestamp.plus(100.millis), "200"))

      finished.foreach(sut.requestCompleted)

      buffer should have size (events.size - finished.size)
      buffer.values should contain theSameElementsAs events.drop(5)
  }

  it should "return same storage instance and corresponding starte event" in test { case (_, sut) =>
    val id             = createUniqueId
    val startTimestamp = Timestamp.create()
    val path           = "/some/path/"

    sut.requestStarted(RequestStarted(id, startTimestamp, path, "GET"))

    val Some((resultStorage, started)) =
      sut.requestCompleted(RequestCompleted(id, startTimestamp.plus(123.millis), "200"))

    resultStorage should be theSameInstanceAs sut
    started.id should be(id)
  }

  it should "remove stared event from internal buffer when requestFailed is fired" in test { case (buffer, sut) =>
    val startTimestamp = Timestamp.create()
    val events = List.fill(10) {
      val id = createUniqueId
      RequestStarted(id, startTimestamp, "/some/path", "GET")
    }
    events.foreach(sut.requestStarted)
    val finished = events
      .take(5)
      .map(started => RequestFailed(started.id, startTimestamp.plus(100.millis)))

    finished.foreach(sut.requestFailed)

    buffer should have size (events.size - finished.size)
    buffer.values should contain theSameElementsAs events.drop(5)
  }

  it should "return None for requestCompleted if no corresponding started event is present" in test {
    case (buffer, sut) =>
      val startTimestamp = Timestamp.create()
      val events = List.fill(10) {
        val id = createUniqueId
        RequestStarted(id, startTimestamp, "/some/path", "GET")
      }
      events.foreach(sut.requestStarted)
      sut.requestCompleted(RequestCompleted(createUniqueId, startTimestamp.plus(100.millis), "200")) should be(None)
      buffer.values should contain theSameElementsAs events
  }

  it should "return None for requestFailed if no corresponding started event is present" in test { case (buffer, sut) =>
    val startTimestamp = Timestamp.create()
    val events = List.fill(10) {
      val id = createUniqueId
      RequestStarted(id, startTimestamp, "/some/path", "GET")
    }
    events.foreach(sut.requestStarted)
    sut.requestFailed(RequestFailed(createUniqueId, startTimestamp.plus(100.millis))) should be(None)
    buffer.values should contain theSameElementsAs events
  }
}
