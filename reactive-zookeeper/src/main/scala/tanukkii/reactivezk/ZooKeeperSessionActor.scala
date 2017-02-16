package tanukkii.reactivezk

import java.util.concurrent.TimeUnit
import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.Watcher.Event.KeeperState._
import scala.concurrent.duration.FiniteDuration

case class ZKSessionSettings(connectString: String, sessionTimeout: FiniteDuration, connectionTimeout: FiniteDuration)

case class ZKSessionSupervisorSettings(props: Props, childName: String, isConnectionStateAware: Boolean)

object ZooKeeperSession {
  case object Close
  case object Closed
  case object Restart
  case object Restarted
}

@SerialVersionUID(1L) case class ZooKeeperSessionRestartException(sender: Option[ActorRef]) extends Exception("Closing the ZooKeeper session and will reestablish a new session")

@SerialVersionUID(1L) class ConnectedRecoveryTimeoutException(timeout: FiniteDuration) extends Exception(s"ZooKeeper connection did not recover from Disconnected state after $timeout")

private [reactivezk] class ZooKeeperSessionActor(settings: ZKSessionSettings, supervisorSettings: Option[ZKSessionSupervisorSettings]) extends Actor
with ActorLogging with WatcherCallback{
  import WatcherConversion._
  import ZooKeeperSession._
  import context.dispatcher

  var connected = false
  var closed = false

  val zookeeper = new ZooKeeper(settings.connectString, settings.sessionTimeout.toMillis.toInt, watchCallback(self))

  val zookeeperOperation: ActorRef = context.actorOf(ZooKeeperOperationActor.props(zookeeper), "zookeeper-operation")

  val childActorOpt: Option[ActorRef] = supervisorSettings.map(s => context.actorOf(s.props, s.childName))

  def receive: Receive = {
    case ZooKeeperWatchEvent(e) => {
      log.info(e.toString)
      e.getState match {
        case SyncConnected =>
          connected = true
          notifyConnectionState(SyncConnectedState)
        case Disconnected =>
          connected = false
          if (settings.connectionTimeout == FiniteDuration(0L, TimeUnit.SECONDS)) {
            throw new ConnectedRecoveryTimeoutException(settings.connectionTimeout)
          } else {
            context.system.scheduler.scheduleOnce(settings.connectionTimeout, self, ZooKeeperSessionActor.DisconnectedTimeout)
          }
          notifyConnectionState(DisconnectedState)
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
    case cmd: ZKOperations.ZKCommand => zookeeperOperation forward cmd
    case other => childActorOpt.foreach(_ forward other)
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

  private def notifyConnectionState(message: ZooKeeperConnectionState): Unit = {
    for {
      s <- supervisorSettings
      if s.isConnectionStateAware
      ref <- childActorOpt
    } {
      ref ! message
    }
  }
}

object ZooKeeperSessionActor {
  def props(settings: ZKSessionSettings, supervisorSettings: ZKSessionSupervisorSettings) = Props(new ZooKeeperSessionActor(settings, Some(supervisorSettings)))
  def props(connectString: String, sessionTimeout: FiniteDuration): Props = Props(new ZooKeeperSessionActor(ZKSessionSettings(connectString, sessionTimeout, sessionTimeout), None))

  private case object DisconnectedTimeout
}