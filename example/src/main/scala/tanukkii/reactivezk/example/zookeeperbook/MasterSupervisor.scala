package tanukkii.reactivezk.example.zookeeperbook

import java.util.Random

import akka.actor.SupervisorStrategy.{Restart, Decider, Stop}
import akka.actor._
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.Watcher.Event.KeeperState._
import tanukkii.reactivezk.ReactiveZK
import tanukkii.reactivezk.ZooKeeperActorProtocol.ZooKeeperWatchEvent

class MasterSupervisor extends Actor with ActorLogging{
  import BootstrapProtocol._
  import MasterProtocol._

  context.system.eventStream.subscribe(self, classOf[ZooKeeperWatchEvent])

  val zookeeperSession = ReactiveZK(context.system).zookeeperSession

  val random = new Random(this.hashCode())

  val bootstrap = context.actorOf(Bootstrap.props(zookeeperSession, self), "bootstrap")

  val master = context.actorOf(Master.props(Integer.toHexString(random.nextInt()), zookeeperSession, self), "master")

  override def supervisorStrategy: SupervisorStrategy = {
    val keeperDecider: Decider = {
      case e: KeeperException => log.error(e, "ZooKeeper error"); Stop
    }
    OneForOneStrategy()(keeperDecider orElse SupervisorStrategy.defaultDecider)
  }

  def receive: Receive = {
    case ZooKeeperWatchEvent(e) if e.getState == SyncConnected => {
      context.system.eventStream.unsubscribe(self, classOf[ZooKeeperWatchEvent])
      context.become(bootstrapping)
      bootstrap ! CreateParents
    }
  }


  def bootstrapping: Receive = {
    case ParentCreated => {
      context.become(masterElecting)
      master ! RunForMaster
    }
  }

  def masterElecting: Receive = {
    case MasterElectionEnd(status) => log.info("master is {}", status)
  }

}

object MasterSupervisor {
  def props: Props = Props(new MasterSupervisor)
}