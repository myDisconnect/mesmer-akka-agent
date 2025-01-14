package io.scalac.mesmer.agent.akka

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Deregister
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Connection
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps

import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent
import io.scalac.mesmer.agent.utils.InstallModule
import io.scalac.mesmer.core.event.HttpEvent
import io.scalac.mesmer.core.event.HttpEvent.ConnectionCompleted
import io.scalac.mesmer.core.event.HttpEvent.ConnectionStarted
import io.scalac.mesmer.core.event.HttpEvent.RequestCompleted
import io.scalac.mesmer.core.event.HttpEvent.RequestStarted
import io.scalac.mesmer.core.httpServiceKey
import io.scalac.mesmer.core.util.TestOps

class AkkaHttpAgentTest
    extends InstallModule(AkkaHttpAgent)
    with ScalatestRouteTest
    with AnyFlatSpecLike
    with Matchers
    with OptionValues
    with TestOps {

  override def testConfig: Config = ConfigFactory.load("application-test")

  type Fixture = TestProbe[HttpEvent]

  val testRoute: Route = path("test") {
    get {
      complete((StatusCodes.OK, collection.immutable.Seq(Connection("close"))))
    }
  }

  def test(body: Fixture => Any): Any = {
    implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped
    val monitor                                          = TestProbe[HttpEvent]("http-test-probe")
    Receptionist(typedSystem).ref ! Register(httpServiceKey, monitor.ref)
    body(monitor)
    Receptionist(typedSystem).ref ! Deregister(httpServiceKey, monitor.ref)
  }

  "AkkaHttpAgent" should "instrument routes to generate events on http requests" in test { monitor =>
    implicit val timeout = RouteTestTimeout(5 seconds)

    Get("/test") ~!> testRoute ~> check {
      status should be(StatusCodes.OK)
    }
    monitor.expectMessageType[ConnectionStarted]
    monitor.expectMessageType[RequestStarted]
    monitor.expectMessageType[RequestCompleted]
    monitor.expectMessageType[ConnectionCompleted]
  }

}
