package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import akka.actor.{ ActorContext, ActorRef }

class ClassicStashInstrumentation
object ClassicStashInstrumentation {

  import Getters._

  @OnMethodExit
  def onStashExit(@This stash: Any): Unit =
    StashInstrumentation.publish(getStashSize(stash), getActorRef(stash), getContext(stash))

  private object Getters {

    private val lookup = MethodHandles.lookup()

    private val stashSupportClass = Class.forName("akka.actor.StashSupport")

    // Disclaimer:  The way we access the stash vector of and StashSupport is a quite ugly because it's an private field.
    //              We discovered its name during the debug and we aren't sure if this pattern is consistent through the compiler variations and versions.
    private val theStashMethodHandle =
      lookup.findVirtual(stashSupportClass, "akka$actor$StashSupport$$theStash", methodType(classOf[Vector[_]]))

    private val getSelfMethodHandle =
      lookup.findVirtual(stashSupportClass, "self", methodType(classOf[ActorRef]))

    private val getContextMethodHandle =
      lookup.findVirtual(stashSupportClass, "context", methodType(classOf[ActorContext]))

    @inline def getStashSize(stashSupport: Any): Int =
      theStashMethodHandle.invoke(stashSupport).asInstanceOf[Vector[_]].length

    @inline def getActorRef(stashSupport: Any): ActorRef =
      getSelfMethodHandle.invoke(stashSupport).asInstanceOf[ActorRef]

    @inline def getContext(stashSupport: Any): ActorContext =
      getContextMethodHandle.invoke(stashSupport).asInstanceOf[ActorContext]

  }

}