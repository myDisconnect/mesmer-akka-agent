package io.scalac.agent.akka

import scala.concurrent._
import scala.concurrent.duration._

import akka.actor.PoisonPill
import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import akka.{ actor => classic }

import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.time.{ Millis, Span }
import org.scalatest.{ BeforeAndAfterAll, OptionValues }

import io.scalac.core.model._
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }
import io.scalac.extension.actorServiceKey
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.util.ReceptionistOps

class AkkaActorAgentTest
    extends ScalaTestWithActorTestKit({
      installAgent
      classic.ActorSystem("AkkaActorAgentTest").toTyped
    })
    with AnyFlatSpecLike
    with ReceptionistOps
    with BeforeAndAfterAll
    with OptionValues
    with Eventually {

  import AkkaActorAgentTest._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig().copy(scaled(Span(1000, Millis)))

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[ActorEvent]
    Receptionist(system).ref ! Register(actorServiceKey, monitor.ref)
    onlyRef(monitor.ref, actorServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(actorServiceKey, monitor.ref)
    monitor.stop()
  }

  "AkkaActorAgent" should "record mailbox time properly" in {
    val idle      = 100.milliseconds
    val tolerance = 50
    testWithContextAndActor[String](_ =>
      Behaviors.receiveMessage {
        case "idle" =>
          Thread.sleep(idle.toMillis)
          Behaviors.same
        case _ =>
          Behaviors.same
      }
    ) { (ctx, actor) =>
      val n       = 3
      val waiting = n - 1
      actor ! "idle"
      for (_ <- 0 until waiting) actor ! "42"
      eventually {
        val metrics = ActorTimesDecorators.MailboxTime.getMetrics(ctx).value
        metrics.count should be(n)
        metrics.avg should be(((waiting * idle.toMillis) / n) +- tolerance)
        metrics.sum should be((waiting * idle.toMillis) +- tolerance)
        metrics.min should be(0L +- tolerance)
        metrics.max should be(idle.toMillis +- tolerance)
      }
    }
  }

  it should "record classic stash properly" in test { monitor =>
    val stashActor                   = system.classicSystem.actorOf(ClassicStashActor.props(), "stashActor")
    val expectStashSize: Int => Unit = createExpectStashSize(monitor, "/user/stashActor")
    stashActor ! Message("random")
    expectStashSize(1)
    stashActor ! Message("42")
    expectStashSize(2)
    stashActor ! Open
    expectStashSize(0)
    stashActor ! Message("normal")
    monitor.expectNoMessage()
    stashActor ! Close
    stashActor ! Message("emanuel")
    expectStashSize(1)
  }

  it should "record typed stash properly" in test { monitor =>
    val stashActor                   = system.systemActorOf(TypedStash(10), "typedStashActor")
    val expectStashSize: Int => Unit = createExpectStashSize(monitor, stashActor)
    stashActor ! Message("random")
    expectStashSize(1)
    stashActor ! Message("42")
    expectStashSize(2)
    stashActor ! Open
    expectStashSize(0)
    stashActor ! Message("normal")
    monitor.expectNoMessage()
    stashActor ! Close
    stashActor ! Message("emanuel")
    expectStashSize(1)
  }

  it should "record the amount of received messages" in testWithContextAndActor[String](_ => Behaviors.ignore) {
    (ctx, actor) =>
      def received(size: Int): Unit = {
        eventually {
          ActorCountsDecorators.Received.getValue(ctx).value should be(size)
        }
        ActorCountsDecorators.Received.reset(ctx)
      }

      received(0)
      actor ! "42"
      received(1)
      received(0)
      actor ! "42"
      actor ! "42"
      received(2)
  }

  it should "record the amount of failed messages without supervision" in testWithContextAndActor[String](_ =>
    Behaviors.receiveMessage {
      case "fail" =>
        throw new RuntimeException("I failed :(")
      case _ =>
        Behaviors.same
    }
  ) { (ctx, actor) =>
    def failed(size: Int): Unit = {
      eventually {
        ActorCountsDecorators.Failed.getValue(ctx).value should be(size)
      }
      ActorCountsDecorators.Failed.reset(ctx)
    }

    failed(0)
    actor ! "fail"
    failed(1)
    failed(0)
    actor ! ":)"
    failed(0)
    actor ! "fail"
    failed(0) // why zero? because akka suspend any further message processing after an unsupervisioned failure
  }

  it should "record the amount of failed messages with supervision" in {

    def testForStrategy(strategy: SupervisorStrategy): Unit = testWithContextAndActor[String](_ =>
      Behaviors
        .supervise[String](
          Behaviors.receiveMessage {
            case "fail" =>
              throw new RuntimeException(s"[strategy = $strategy]I failed :(")
            case _ =>
              Behaviors.same
          }
        )
        .onFailure[RuntimeException](strategy)
    ) { (ctx, actor) =>
      def failed(size: Int): Unit = {
        eventually {
          ActorCountsDecorators.Failed.getValue(ctx).value should be(size)
        }
        ActorCountsDecorators.Failed.reset(ctx)
      }

      failed(0)
      actor ! "fail"
      failed(1)
      failed(0)
      actor ! ":)"
      failed(0)
      actor ! "fail"
      actor ! "fail"
      if (strategy != SupervisorStrategy.stop) failed(2)
      failed(0)
    }

    testForStrategy(SupervisorStrategy.restart)
    testForStrategy(SupervisorStrategy.resume)
    testForStrategy(SupervisorStrategy.stop)
  }

  it should "record the amount of unhandled messages" in testWithContextAndActor[String](_ =>
    Behaviors.receiveMessage {
      case "receive" => Behaviors.same
      case _         => Behaviors.unhandled
    }
  ) { (ctx, actor) =>
    def unhandled(size: Int): Unit = {
      eventually {
        ActorCountsDecorators.Unhandled.getValue(ctx).value should be(size)
      }
      ActorCountsDecorators.Unhandled.reset(ctx)
    }

    unhandled(0)
    actor ! "42"
    unhandled(1)
    unhandled(0)
    actor ! "42"
    actor ! "42"
    unhandled(2)
    actor ! "receive"
    unhandled(0)
  }

  it should "record processing time properly" in {
    val processing = 100.milliseconds
    val tolerance  = 50
    testWithContextAndActor[String](_ =>
      Behaviors.receiveMessage {
        case "work" =>
          Thread.sleep(processing.toMillis)
          Behaviors.same
        case _ =>
          Behaviors.same
      }
    ) { (ctx, actor) =>
      val n       = 3
      val working = n - 1
      actor ! "42"
      for (_ <- 0 until working) actor ! "work"
      eventually {
        val metrics = ActorTimesDecorators.ProcessingTime.getMetrics(ctx).value
        metrics.count should be(n)
        metrics.avg should be(((working * processing.toMillis) / n) +- tolerance)
        metrics.sum should be((working * processing.toMillis) +- tolerance)
        metrics.min should be(0L +- tolerance)
        metrics.max should be(processing.toMillis +- tolerance)
      }
    }
  }

  def createExpectStashSize(monitor: Fixture, ref: ActorRef[_]): Int => Unit =
    createExpectStashSize(monitor, ActorPathOps.getPathString(ref))

  def createExpectStashSize(monitor: Fixture, path: ActorPath): Int => Unit = { size =>
    val msg = monitor.fishForMessage(2.seconds) {
      case StashMeasurement(`size`, `path`) => FishingOutcome.Complete
      case _                                => FishingOutcome.ContinueAndIgnore
    }
    msg.size should not be (0)
  }

  def testWithContextAndActor[T](
    behavior: ActorContext[T] => Behavior[T]
  )(
    block: (classic.ActorContext, ActorRef[T]) => Unit
  ): Unit = {
    var ctxRef: Option[classic.ActorContext] = None
    val testActor = spawn(
      Behaviors.setup[T] { ctx =>
        ctxRef = Some(ctx.toClassic)
        behavior(ctx)
      },
      createUniqueId
    )
    Await.ready(
      Future {
        blocking {
          while (ctxRef.isEmpty) {}
        }
      }(ExecutionContext.global),
      2.seconds
    )
    block(ctxRef.get, testActor)
    testActor.unsafeUpcast[Any] ! PoisonPill
  }

}

object AkkaActorAgentTest {

  type Fixture = TestProbe[ActorEvent]

  sealed trait Command
  final case object Open                 extends Command
  final case object Close                extends Command
  final case class Message(text: String) extends Command

  object ClassicStashActor {
    def props(): classic.Props = classic.Props(new ClassicStashActor)
  }
  class ClassicStashActor extends classic.Actor with classic.Stash with classic.ActorLogging {
    def receive: Receive = {
      case Open =>
        unstashAll()
        context
          .become({
            case Close =>
              context.unbecome()
            case Message(text) =>
              log.warning(s"[working on] {}", text)
          })
      case Message(text) =>
        log.warning(s"[stash] {}", text)
        stash()
    }
  }

  object TypedStash {
    def apply(capacity: Int): Behavior[Command] =
      Behaviors.setup(ctx => Behaviors.withStash(capacity)(buffer => new TypedStash(ctx, buffer).closed()))
  }

  class TypedStash(ctx: ActorContext[Command], buffer: StashBuffer[Command]) {
    import ctx.log
    private def closed(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case message @ Message(text) =>
          log.warn(s"[typed] [stashing] {}", text)
          buffer.stash(message)
          Behaviors.same
      }
    private def open(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Close =>
          closed()
        case Message(text) =>
          log.warn(s"[typed] [working on] {}", text)
          Behaviors.same
      }

  }

}
