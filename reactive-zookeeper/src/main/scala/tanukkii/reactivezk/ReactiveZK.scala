package tanukkii.reactivezk

import akka.actor._
import scala.concurrent.duration._

private[reactivezk] class ReactiveZKImpl(system: ExtendedActorSystem) extends Extension {
  private val config = system.settings.config.getConfig("reactive-zookeeper")

  lazy val zookeeperSession: ActorRef = system.actorOf(ZooKeeperSessionActor.props(
    config.getString("connect-string"),
    config.getInt("session-timeout") millis)
    , "zookeeper-session")
}

object ReactiveZK extends ExtensionId[ReactiveZKImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ReactiveZKImpl = new ReactiveZKImpl(system)

  override def lookup(): ExtensionId[_ <: Extension] = ReactiveZK

  override def get(system: ActorSystem): ReactiveZKImpl = super.get(system)
}