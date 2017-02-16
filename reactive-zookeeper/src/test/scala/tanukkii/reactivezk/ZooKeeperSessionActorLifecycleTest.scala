package tanukkii.reactivezk

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.testkit.{ TestKit, TestProbe }
import org.apache.zookeeper.Watcher.Event.{ EventType, KeeperState }
import org.apache.zookeeper.WatchedEvent
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._


private class EchoActor(replyTo: ActorRef) extends Actor {
  def receive: Receive = {
    case msg => replyTo ! msg
  }

  override def preStart(): Unit = {
    replyTo ! "Started"
    super.preStart()
  }
}

private object EchoActor {
  def props(replyTo: ActorRef): Props = Props(new EchoActor(replyTo))
}

private class ConnectionStateAwareEchoActor(replyTo: ActorRef) extends ZKConnectionStateAwareActor {
  def receiveSyncConnected: Receive = {
    case msg => replyTo ! msg
  }

  def receiveDisconnected: Receive = {
    case msg => replyTo ! "Disconnected"
  }

  override def preStart(): Unit = {
    replyTo ! "Started"
    super.preStart()
  }
}

private object ConnectionStateAwareEchoActor {
  def props(replyTo: ActorRef): Props = Props(new ConnectionStateAwareEchoActor(replyTo))
}

class ZooKeeperSessionActorLifecycleTest  extends TestKit(ActorSystem("ZooKeeperSessionActorTest"))
  with WordSpecLike with ZooKeeperTest with Matchers with StopSystemAfterAll {

  val dataDir: String = "target/zookeeper/ZooKeeperSessionActorLifecycleTest"

  "ZooKeeperSessionActor" must {
    "forward messages to child actor" in {
      val probe = TestProbe()
      val settings = ZKSessionSettings(zkConnectString, 5 seconds, 5 seconds)
      val supervisorSettings = ZKSessionSupervisorSettings(EchoActor.props(probe.ref), "echo", false)
      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(settings, supervisorSettings))
      probe.expectMsg("Started")
      zooKeeperActor ! "test"
      probe.expectMsg("test")
    }

    "restart if connectionTimeout passed after Disconnected" in {
      val probe = TestProbe()
      val settings = ZKSessionSettings(zkConnectString, 5 seconds, 1 seconds)
      val supervisorSettings = ZKSessionSupervisorSettings(EchoActor.props(probe.ref), "echo", isConnectionStateAware = false)
      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(settings, supervisorSettings))
      probe.expectMsg("Started")

      // ensure to be SyncConnected state
      probe.send(zooKeeperActor, ZKOperations.Exists("/test"))
      probe.expectMsgType[ZKOperations.ExistsFailure]

      zooKeeperActor ! ZooKeeperWatchEvent(new WatchedEvent(EventType.None, KeeperState.Disconnected, ""))
      probe.expectNoMsg(1 second)
      probe.expectMsg("Started")
    }

    "restart immediately after Disconnected when connectionTimeout=0s" in {
      val probe = TestProbe()
      val settings = ZKSessionSettings(zkConnectString, 5 seconds, 0 seconds)
      val supervisorSettings = ZKSessionSupervisorSettings(EchoActor.props(probe.ref), "echo", isConnectionStateAware = false)
      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(settings, supervisorSettings))
      probe.expectMsg("Started")
      zooKeeperActor ! ZooKeeperWatchEvent(new WatchedEvent(EventType.None, KeeperState.Disconnected, ""))
      probe.expectMsg("Started")
    }

    "restart when session is expired" in {
      val probe = TestProbe()
      val settings = ZKSessionSettings(zkConnectString, 5 seconds, 1 seconds)
      val supervisorSettings = ZKSessionSupervisorSettings(EchoActor.props(probe.ref), "echo", isConnectionStateAware = false)
      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(settings, supervisorSettings))
      probe.expectMsg("Started")
      zooKeeperActor ! ZooKeeperWatchEvent(new WatchedEvent(EventType.None, KeeperState.Expired, ""))
      probe.expectMsg("Started")
    }

    "notify connection state to ZKConnectionStateAwareActor to switch behavior" in {
      val probe = TestProbe()
      val settings = ZKSessionSettings(zkConnectString, 5 seconds, 5 seconds)
      val supervisorSettings = ZKSessionSupervisorSettings(ConnectionStateAwareEchoActor.props(probe.ref), "echo", isConnectionStateAware = true)
      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(settings, supervisorSettings))
      probe.expectMsg("Started")
      zooKeeperActor ! "test"
      probe.expectMsg("test")
      zooKeeperActor ! ZooKeeperWatchEvent(new WatchedEvent(EventType.None, KeeperState.Disconnected, ""))
      zooKeeperActor ! "test"
      probe.expectMsg("Disconnected")
    }
  }

}
