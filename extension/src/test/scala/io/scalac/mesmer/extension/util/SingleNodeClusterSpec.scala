package io.scalac.mesmer.extension.util

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Member
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.typed.Cluster
import akka.cluster.typed.SelfUp
import akka.cluster.typed.Subscribe
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.scalatest.Assertion
import org.scalatest.AsyncTestSuite

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

import io.scalac.mesmer.core.util.PortGenerator
import io.scalac.mesmer.core.util.PortGeneratorImpl
import io.scalac.mesmer.core.util.probe.ObserverCollector.ScheduledCollectorImpl
import io.scalac.mesmer.extension.util.probe.ClusterMonitorTestProbe

trait SingleNodeClusterSpec extends AsyncTestSuite {

  protected val portGenerator: PortGenerator = PortGeneratorImpl
  implicit val timeout: Timeout              = 30.seconds
  protected val pingOffset: FiniteDuration   = 1.seconds

  type Fixture[C[_], T] =
    (ActorSystem[Nothing], Member, C[ActorRef[ShardingEnvelope[T]]], ClusterMonitorTestProbe, C[String])
  type Id[T] = T

  protected def createConfig(port: Int, systemName: String): Config = {
    val hostname = "127.0.0.1"
    val seedNode = s"akka://$systemName@$hostname:$port"
    ConfigFactory.empty
      .withValue("akka.actor.provider", ConfigValueFactory.fromAnyRef("cluster"))
      .withValue(
        "akka.remote.artery.canonical",
        ConfigValueFactory.fromMap(Map("hostname" -> "127.0.0.1", "port" -> port).asJava)
      )
      .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(List(seedNode).asJava))
  }

  def setupN[T: ClassTag](behavior: String => Behavior[T], n: Int)(
    test: Fixture[Seq, T] => Assertion
  ): Future[Assertion] = {
    val port = portGenerator.generatePort()

    def initAkka(): Future[(List[String], ActorSystem[Nothing], Cluster, ClusterSharding)] = Future {
      val systemId     = UUID.randomUUID().toString
      val entityNames  = List.tabulate(n)(_ => UUID.randomUUID().toString)
      val systemConfig = createConfig(port.port, systemId)

      implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, systemId, systemConfig)

      // TODO consider using blocking dispatcher
      val cluster = Cluster(system)

      val sharding = ClusterSharding(system)

      (entityNames, system, cluster, sharding)
    }

    def runTest(implicit
      system: ActorSystem[Nothing],
      entityNames: List[String],
      cluster: Cluster,
      sharding: ClusterSharding
    ): Future[Assertion] = Future {

      // sharding boots-up synchronously
      val refs = entityNames.map { entityName =>
        val entityKey = EntityTypeKey[T](entityName)
        val entity    = Entity(entityKey)(context => behavior(context.entityId))
        sharding.init(entity)
      }

      val collector: ScheduledCollectorImpl = new ScheduledCollectorImpl(pingOffset)

      val clusterProbe = ClusterMonitorTestProbe(collector)

      Function.untupled(test)(system, cluster.selfMember, refs, clusterProbe, entityNames)
    }

    def onClusterStart(cluster: Cluster)(implicit system: ActorSystem[_]) =
      cluster.subscriptions.ask[SelfUp](reply => Subscribe(reply, classOf[SelfUp]))

    for {

      (regions, _system, cluster, sharding) <- initAkka()

      system = _system

      _ <- onClusterStart(cluster)(system)

      assertion <-
        runTest(system, regions, cluster, sharding).andThen { case _ =>
          portGenerator.releasePort(port)
          system.terminate()
        }

      _ <- system.whenTerminated

    } yield assertion

  }

  def setup[T: ClassTag](behavior: String => Behavior[T])(test: Fixture[Id, T] => Assertion): Future[Assertion] =
    setupN(behavior, n = 1) { case (system, member, Seq(ref), probe, Seq(shading)) =>
      test(system, member, ref, probe, shading)
    }

}
