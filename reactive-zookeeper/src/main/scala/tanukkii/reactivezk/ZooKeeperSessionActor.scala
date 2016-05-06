package tanukkii.reactivezk

import akka.actor.{ActorLogging, Props, Actor}
import org.apache.zookeeper.{WatchedEvent, ZooKeeper}
import org.apache.zookeeper.Watcher.Event.KeeperState._
import scala.concurrent.duration.FiniteDuration


object ZooKeeperSession {
  case object Close
  case object Closed
  case object Restart
}

@SerialVersionUID(1L) class ZooKeeperSessionRestartException() extends Exception("closing the ZooKeeper session and will reestablish a new session")

private [reactivezk] class ZooKeeperSessionActor(connectString: String, sessionTimeout: FiniteDuration) extends Actor
with ActorLogging with WatcherCallback{
  import WatcherConversion._
  import ZooKeeperSession._

  var connected = false
  var expired = false
  var closed = false

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
      close()
      closed = true
      sender() ! Closed
      context.stop(self)
    }
    case Restart => throw new ZooKeeperSessionRestartException()
    case other => zookeeperOperation forward other
  }

  override def postStop(): Unit = {
    if (!closed) close()
    super.postStop()
  }

  def close() = {
    zookeeper.close()
    log.info("ZooKeeper session is closed.")
  }
}

object ZooKeeperSessionActor {
  def props(connectString: String, sessionTimeout: FiniteDuration): Props = Props(new ZooKeeperSessionActor(connectString, sessionTimeout))
}