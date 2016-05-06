package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.WatchedEvent

trait WatcherCallback {
  def watchCallback(sendTo: ActorRef)(implicit sender: ActorRef = Actor.noSender): (WatchedEvent) => Unit = {
    (e: WatchedEvent) => sendTo ! ZooKeeperWatchEvent(e)
  }
}
