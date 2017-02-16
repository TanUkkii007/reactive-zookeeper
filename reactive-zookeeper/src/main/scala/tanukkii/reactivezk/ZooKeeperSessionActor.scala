package tanukkii.reactivezk

import java.util.concurrent.TimeUnit
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState._
import scala.concurrent.duration.FiniteDuration

case class ZooKeeperSessionSettings(connectString: String, sessionTimeout: FiniteDuration, connectionTimeout: FiniteDuration)

object ZooKeeperSession {
  case object Close
  case object Closed
  case object Restart
  case object Restarted
}

@SerialVersionUID(1L) case class ZooKeeperSessionRestartException(sender: Option[ActorRef]) extends Exception("Closing the ZooKeeper session and will reestablish a new session")

@SerialVersionUID(1L) class ConnectedRecoveryTimeoutException(timeout: FiniteDuration) extends Exception(s"ZooKeeper connection did not recover from Disconnected state after $timeout")

private [reactivezk] class ZooKeeperSessionActor(settings: ZooKeeperSessionSettings) extends Actor
with ActorLogging with WatcherCallback{
  import WatcherConversion._
  import ZooKeeperSession._
  import context.dispatcher

  var connected = false
  var closed = false

  val zookeeper = new ZooKeeper(settings.connectString, settings.sessionTimeout.toMillis.toInt, watchCallback(self))

  val zookeeperOperation: ActorRef = context.actorOf(ZooKeeperOperationActor.props(zookeeper), "zookeeper-operation")

  def receive: Receive = {
    case ZooKeeperWatchEvent(e) => {
      log.info(e.toString)
      e.getState match {
        case SyncConnected => connected = true
        case Disconnected =>
          connected = false
          if (settings.connectionTimeout == FiniteDuration(0L, TimeUnit.SECONDS)) {
            throw new ConnectedRecoveryTimeoutException(settings.connectionTimeout)
          } else {
            context.system.scheduler.scheduleOnce(settings.connectionTimeout, self, ZooKeeperSessionActor.DisconnectedTimeout)
          }
        case Expired => throw ZooKeeperSessionRestartException(None)
        case _ =>
      }
    }
    case Close => {
      close()
      closed = true
      sender() ! Closed
      context.stop(self)
    }
    case Restart => throw ZooKeeperSessionRestartException(Some(sender()))
    case ZooKeeperSessionActor.DisconnectedTimeout if !connected =>
      throw new ConnectedRecoveryTimeoutException(settings.connectionTimeout)
    case other => zookeeperOperation forward other
  }

  override def postStop(): Unit = {
    if (!closed) close()
    super.postStop()
  }

  override def postRestart(reason: Throwable): Unit = {
    reason match {
      case ZooKeeperSessionRestartException(Some(ref)) =>
        ref ! Restarted
      case _ =>
    }
    super.postRestart(reason)
  }

  private[this] def close(): Unit = {
    zookeeper.close()
    log.info("ZooKeeper session is closed.")
  }
}

object ZooKeeperSessionActor {
  def props(settings: ZooKeeperSessionSettings) = Props(new ZooKeeperSessionActor(settings))
  def props(connectString: String, sessionTimeout: FiniteDuration): Props = Props(new ZooKeeperSessionActor(ZooKeeperSessionSettings(connectString, sessionTimeout, sessionTimeout)))

  private case object DisconnectedTimeout
}