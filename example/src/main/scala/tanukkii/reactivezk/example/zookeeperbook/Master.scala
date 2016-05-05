package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.ZooDefs.Ids
import tanukkii.reactivezk.ZooKeeperActorProtocol.ZooKeeperWatchEvent
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
  case object MasterExists
  case object GetWorkers
  case class MasterElectionEnd(status: MasterState)
}

class Master(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) extends Actor with ActorLogging {
  import MasterProtocol._
  import ZKOperations._
  import context.dispatcher

  var state: MasterState = MasterStates.Running

  var workersCache = ChildrenCache()
  var toProcess = ChildrenCache()

  def receive: Receive = runForMaster

  def runForMaster: Receive = {
    case RunForMaster => {
      log.info("Running for master")
      zookeeperSession ! Create("/master", serverId.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
    }
    case Created(path, name, _) => {
      state = MasterStates.Elected
      log.info(s"I'm the leader $serverId")
      supervisor ! MasterElectionEnd(state)
      context.become(takeLeadership)
      self ! GetWorkers
    }
    case CreateFailure(e, _) if e.code() == Code.NODEEXISTS => {
      state = MasterStates.NotElected
      log.info(s"I'm not the leader $serverId")
      supervisor ! MasterElectionEnd(state)
      context.become(masterExists)
      self ! MasterExists
    }
    case CreateFailure(e, _) if e.code() == Code.CONNECTIONLOSS => {
      context.become(checkMaster)
      self ! CheckMaster
    }
    case CreateFailure(e, _) => throw e
  }

  def checkMaster: Receive = {
    case CheckMaster => zookeeperSession ! GetData("/master")
    case DataGot(path, data, stat, _) => {
      if (data.toString == serverId) {
        state = MasterStates.Elected
        context.become(takeLeadership)
        self ! GetWorkers
      } else {
        state = MasterStates.NotElected
      }
      context.become(runForMaster)
      supervisor ! MasterElectionEnd(state)
    }
    case GetDataFailure(e, _) if e.code() == Code.NONODE => {
      context.become(runForMaster)
      self ! RunForMaster
    }
    case GetDataFailure(e, _) if e.code() == Code.CONNECTIONLOSS => {
      context.system.scheduler.scheduleOnce(1 second, self, CheckMaster)
    }
    case GetDataFailure(e, _) => throw e
  }

  def masterExists: Receive = {
    case MasterExists => zookeeperSession ! Exists("/master", watch = true)
    case DoesExist(path, stat, _) =>
    case ExistsFailure(e, _) if e.code() == Code.CONNECTIONLOSS => {
      self ! MasterExists
    }
    case ExistsFailure(e, _) if e.code() == Code.NONODE => {
      state = MasterStates.Running
      context.become(runForMaster)
      self ! RunForMaster
      log.info("It sounds like the previous master is gone, so let's run for master again.")
    }
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeDeleted => {
      assert("/master" == e.getPath)
      context.become(runForMaster)
      self ! RunForMaster
    }
  }

  def takeLeadership: Receive = {
    case GetWorkers => zookeeperSession ! GetChildren("/workers", watch = true)
    case ChildrenGot(path, children, _) => {
      log.info(s"Succesfully got a list of workers: ${children.size} workers")
      reassignAndSet(children)
    }
    case GetChildrenFailure(e, _) if e.code() == Code.CONNECTIONLOSS => self ! GetWorkers
    case GetChildrenFailure(e, _) => throw e
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeChildrenChanged => {
      assert("/workers" == e.getPath)
      self ! GetWorkers
    }
  }

  def reassignAndSet(children: List[String]) = {
    if (workersCache.isEmpty) {
      workersCache = ChildrenCache(children)
      toProcess = ChildrenCache()
    } else {
      log.info("Removing and setting")
      toProcess = workersCache.diff(ChildrenCache(children))
      workersCache = ChildrenCache(children)
    }
    context.become(getAbsentWorkerTasks)
    toProcess.children.foreach { worker =>
      zookeeperSession ! GetChildren(s"/assign/$worker")
    }
  }

  def getAbsentWorkerTasks: Receive = {
    case ChildrenGot(path, children, _) => {
      log.info(s"Succesfully got a list of assignments: ${children.size} tasks")
      children.foreach { task =>
        zookeeperSession ! GetData(s"$path/$task")
      }
    }
    case GetChildrenFailure(e, _) if e.code() == Code.CONNECTIONLOSS => self ! GetChildren(e.getPath)
    case GetChildrenFailure(e, _) => throw e
  }
}

object Master {
  def props(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) = Props(new Master(serverId, zookeeperSession, supervisor))
}