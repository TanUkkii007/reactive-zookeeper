package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.ZooDefs.Ids
import tanukkii.reactivezk.{IdConversions, ZKOperations}
import scala.concurrent.duration._
import IdConversions._

sealed trait MasterState

object MasterStates {
  case object Running extends MasterState
  case object Elected extends MasterState
  case object NotElected extends MasterState
}

object MasterProtocol {
  case object RunForMaster
  case object CheckMaster
  case class MasterElectionEnd(status: MasterState)
}

class Master(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) extends Actor with ActorLogging {
  import MasterProtocol._
  import ZKOperations._
  import context.dispatcher

  var state: MasterState = MasterStates.Running

  def receive: Receive = runForMaster

  def runForMaster: Receive = {
    case RunForMaster => {
      log.info("Running for master")
      zookeeperSession ! Create("/master", serverId.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
    }
    case Created(path, name) => {
      state = MasterStates.Elected
      supervisor ! MasterElectionEnd(state)
    }
    case CreateFailure(e) if e.code() == Code.NODEEXISTS => {
      state = MasterStates.NotElected
      supervisor ! MasterElectionEnd(state)
    }
    case CreateFailure(e) if e.code() == Code.CONNECTIONLOSS => {
      context.become(checkMaster)
      self ! CheckMaster
    }
    case CreateFailure(e) => throw e
  }

  def checkMaster: Receive = {
    case CheckMaster => zookeeperSession ! GetData("/master", false)
    case DataGot(path, data, stat) => {
      if (data.toString == serverId) {
        state = MasterStates.Elected
      } else {
        state = MasterStates.NotElected
      }
      context.become(runForMaster)
      supervisor ! MasterElectionEnd(state)
    }
    case GetDataFailure(e) if e.code() == Code.NONODE => {
      context.become(runForMaster)
      self ! RunForMaster
    }
    case GetDataFailure(e) if e.code() == Code.CONNECTIONLOSS => {
      context.system.scheduler.scheduleOnce(1 second, self, CheckMaster)
    }
    case GetDataFailure(e) => throw e
  }
}

object Master {
  def props(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) = Props(new Master(serverId, zookeeperSession, supervisor))
}