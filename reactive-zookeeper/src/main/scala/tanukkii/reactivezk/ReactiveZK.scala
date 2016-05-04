package tanukkii.reactivezk

import akka.actor.{ActorRef, ActorSystem}
import scala.concurrent.duration._

trait ReactiveZK {
  def zookeeperSession: ActorRef
}

private class ReactiveZKImpl(val zookeeperSession: ActorRef) extends ReactiveZK

object ReactiveZK {
  private var session: Option[ActorRef] = None

  def apply(system: ActorSystem): ReactiveZK = synchronized {
    session match {
      case None => {
        val config = system.settings.config
        val ref = system.actorOf(ZooKeeperSessionActor.props(
          config.getString("reactive-zookeeper.connect-string"),
          config.getInt("reactive-zookeeper.session-timeout") millis)
        , "zookeeper-session")
        session = Some(ref)
        new ReactiveZKImpl(ref)
      }
      case Some(ref) => new ReactiveZKImpl(ref)
    }
  }
}