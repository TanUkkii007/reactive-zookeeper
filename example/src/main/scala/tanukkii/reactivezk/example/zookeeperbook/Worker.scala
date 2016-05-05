package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.ZooDefs.Ids
import tanukkii.reactivezk.IdConversions
import tanukkii.reactivezk.ZKOperations._
import IdConversions._
import tanukkii.reactivezk.ZooKeeperActorProtocol.ZooKeeperWatchEvent

object WorkerProtocol {
  case object CreateAssignNode
  case object WorkerBootstrapFinished
  case object Register
  case object Registered
  case object GetTasks
}

class Worker(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) extends Actor with ActorLogging {
  import WorkerProtocol._

  val workerStatusUpdater = context.actorOf(WorkerStatusUpdater.props(zookeeperSession, name, self), "WorkerStatusUpdater")

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
    case CreateFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! CreateAssignNode
    case CreateFailure(e, _, _) if e.code() == Code.NODEEXISTS => {
      log.warning("Assign node already registered")
      context.become(waiting)
      supervisor ! WorkerBootstrapFinished
    }
    case CreateFailure(e, _, _) => throw e
  }

  def register: Receive = {
    case Register => zookeeperSession ! Create(s"/workers/$name", "Idle".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
    case Created(path, name, _) => {
      log.info("Registered successfully: {}", serverId)
      finishRegister()
    }
    case CreateFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! Register
    case CreateFailure(e, _, _) if e.code() == Code.NODEEXISTS => {
      log.warning("Already registered: {}", serverId)
      finishRegister()
    }
    case CreateFailure(e, _, _) => throw e
  }

  def finishRegister() = {
    supervisor ! Registered
    context.become(waiting)
  }

  def getTasks: Receive = {
    case GetTasks => zookeeperSession ! GetChildren(s"/assign/worker-$serverId", watch = true)
    case ChildrenGot(path, children, _) => {
      log.info("Looping into tasks")
      workerStatusUpdater ! WorkerStatusUpdaterProtocol.SetStatus("Working")
      children.foreach { task =>
        log.info("New task: {}", task)
        context.actorOf(TaskSubWorker.props(task, serverId, zookeeperSession)) ! TaskSubWorkerProtocol.ProcessTask
      }
    }
    case GetChildrenFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! GetTasks
    case GetChildrenFailure(e, _, _) => throw e
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeChildrenChanged => {
      assert(s"/assign/worker-$serverId" == e.getPath)
      self ! GetTasks
    }
  }

  def name = s"worker-$serverId"

}

object Worker {
  def props(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef): Props = Props(new Worker(serverId, zookeeperSession, supervisor))
}

object TaskSubWorkerProtocol {
  case object ProcessTask
}

class TaskSubWorker(task: String, serverId: String, zookeeperSession: ActorRef) extends Actor with ActorLogging {
  import TaskSubWorkerProtocol._

  def receive: Receive = {
    case ProcessTask => zookeeperSession ! GetData(s"/assign/worker-$serverId/$task")
    case DataGot(path, data, stat, _) => {
      log.info("Executing your task: {}", new String(data))
      context.become(taskStatus)
      zookeeperSession ! Create(s"/status/$task", "done".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
      zookeeperSession ! Delete(s"/assign/worker-$serverId/$task", -1)
    }
    case GetDataFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! ProcessTask
    case GetDataFailure(e, _, _) => throw e
  }

  def taskStatus: Receive = {
    case Created(path, name, _) => log.info("Created status znode correctly: {}", name)
    case CreateFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => {
      zookeeperSession ! Create(s"/status/$task", "done".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    }
    case CreateFailure(e, path, _) if e.code() == Code.NODEEXISTS => log.warning("Node exists: {}", path)
    case CreateFailure(e, _, _) => throw e
    case Deleted(path, _) =>
      log.info("Task correctly deleted: {}", path)
      finishTask()
    case DeleteFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => finishTask()
    case DeleteFailure(e, _, _) => throw e
  }

  def finishTask(): Unit = context.stop(self)
}

object TaskSubWorker {
  def props(task: String, serverId: String, zookeeperSession: ActorRef): Props = Props(new TaskSubWorker(task, serverId, zookeeperSession))
}

object WorkerStatusUpdaterProtocol {
  case class UpdateStatus(status: String)
  case class SetStatus(status: String)
  case class StatusUpdated(status: String)
}

class WorkerStatusUpdater(zookeeperSession: ActorRef, name: String, replyTo: ActorRef) extends Actor {
  import WorkerStatusUpdaterProtocol._

  def receive: Receive = setStatus

  def setStatus: Receive = {
    case SetStatus(s) => {
      context.become(updateStatus(s))
      zookeeperSession ! SetData(s"/workers/$name", s.getBytes(), -1)
    }
  }

  def updateStatus(status: String): Receive = {
    case DataSet(path, stat, _) => {
      context.become(setStatus)
      replyTo ! StatusUpdated(status)
    }
    case SetDataFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! SetStatus(status)
    case SetStatus(s) if s == status => zookeeperSession ! SetData(s"/workers/$name", s.getBytes(), -1)
  }
}

object WorkerStatusUpdater {
  def props(zookeeperSession: ActorRef, name: String, replyTo: ActorRef): Props = Props(new WorkerStatusUpdater(zookeeperSession, name, replyTo))
}