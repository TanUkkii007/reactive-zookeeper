package tanukkii.reactivezk

import akka.actor.{ActorLogging, Props, Actor}
import org.apache.zookeeper.{WatchedEvent, ZooKeeper}
import org.apache.zookeeper.Watcher.Event.KeeperState._
import scala.concurrent.duration.FiniteDuration


object ZooKeeperActorProtocol {
  case class ZooKeeperWatchEvent(e: WatchedEvent)
  case object Close
  case object Closed
}

private [reactivezk] class ZooKeeperSessionActor(connectString: String, sessionTimeout: FiniteDuration) extends Actor
with ActorLogging with WatcherCallback{
  import WatcherConversion._
  import ZooKeeperActorProtocol._

  var connected = false
  var expired = false

  val zookeeper = new ZooKeeper(connectString, sessionTimeout.toMillis.toInt, watchCallback(self))

  val zookeeperOperation = context.actorOf(ZooKeeperOperationActor.props(zookeeper), "zookeeper-operation")

  def receive: Receive = {
    case ZooKeeperWatchEvent(e) => {
      log.info(e.toString)
      e.getState match {
        case SyncConnected => connected = true
        case Disconnected => connected = false
        case Expired => connected = false; expired = true
        case _ =>
      }
      context.system.eventStream.publish(ZooKeeperWatchEvent(e))
    }
    case Close => {
      zookeeper.close()
      log.info("ZooKeeper session closed")
      sender() ! Closed
    }
    case other => zookeeperOperation forward other
  }

  override def postStop(): Unit = {
    zookeeper.close()
    super.postStop()
  }
}

object ZooKeeperSessionActor {
  def props(connectString: String, sessionTimeout: FiniteDuration): Props = Props(new ZooKeeperSessionActor(connectString, sessionTimeout))
}