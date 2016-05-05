package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.ZooDefs.Ids
import tanukkii.reactivezk.IdConversions
import tanukkii.reactivezk.ZKOperations._
import IdConversions._

object WorkerProtocol {
  case object CreateAssignNode
  case object WorkerBootstrapFinished
  case object Register
  case object Registered
  case class UpdateStatus(status: String)
  case class SetStatus(status: String)
  case class StatusUpdated(status: String)
  case object GetTasks
}

class Worker(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) extends Actor with ActorLogging {
  import WorkerProtocol._

  def receive: Receive = waiting

  def waiting: Receive = {
    case msg@CreateAssignNode => {
      context.become(bootstrap)
      self forward msg
    }
    case msg@Register => {
      context.become(register)
      self forward msg
    }
    case msg@GetTasks => {
      context.become(getTasks)
      self forward msg
    }
  }

  def bootstrap: Receive = {
    case CreateAssignNode => zookeeperSession ! Create(s"/assign/worker-$serverId", Array.empty, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    case Created(path, name, _) => {
      log.info("Assign node created")
      context.become(waiting)
      supervisor ! WorkerBootstrapFinished
    }
    case CreateFailure(e, _) if e.code() == Code.CONNECTIONLOSS => self ! CreateAssignNode
    case CreateFailure(e, _) if e.code() == Code.NODEEXISTS => {
      log.warning("Assign node already registered")
      context.become(waiting)
      supervisor ! WorkerBootstrapFinished
    }
    case CreateFailure(e, _) => throw e
  }

  def register: Receive = {
    case Register => zookeeperSession ! Create(s"/workers/$name", "Idle".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
    case Created(path, name, _) => {
      log.info("Registered successfully: {}", serverId)
      finishRegister()
    }
    case CreateFailure(e, _) if e.code() == Code.CONNECTIONLOSS => self ! Register
    case CreateFailure(e, _) if e.code() == Code.NODEEXISTS => {
      log.warning("Already registered: {}", serverId)
      finishRegister()
    }
    case CreateFailure(e, _) => throw e
  }

  def finishRegister() = {
    supervisor ! Registered
    context.become(waiting)
  }

  def setStatus: Receive = {
    case SetStatus(s) => {
      context.become(updateStatus(s))
      zookeeperSession ! SetData(s"/workers/$name", s.getBytes(), -1)
    }
  }

  def updateStatus(status: String): Receive = {
    case DataSet(path, stat, _) => {
      context.become(setStatus)
      supervisor ! StatusUpdated(status)
    }
    case SetDataFailure(e, _) if e.code() == Code.CONNECTIONLOSS => self ! SetStatus(status)
    case SetStatus(s) if s == status => zookeeperSession ! SetData(s"/workers/$name", s.getBytes(), -1)
  }

  def getTasks: Receive = {
    case GetTasks => zookeeperSession ! GetChildren(s"/assign/worker-$serverId", watch = true)
    case ChildrenGot(path, children, _) => {
      log.info("Looping into tasks")
      context.become(setStatus)
      self ! SetStatus("Working")
    }
    case GetChildrenFailure(e, _) if e.code() == Code.CONNECTIONLOSS => self ! GetTasks
    case GetChildrenFailure(e, _) => throw e
  }

  def name = s"worker-$serverId"

}

object Worker {
  def props(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef): Props = Props(new Worker(serverId, zookeeperSession, supervisor))
}