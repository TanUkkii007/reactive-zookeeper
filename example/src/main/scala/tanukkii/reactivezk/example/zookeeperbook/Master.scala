package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.Watcher.Event.EventType
import org.apache.zookeeper.ZooDefs.Ids
import tanukkii.reactivezk._
import scala.concurrent.duration._
import scala.util.Random

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
  case object GetTasks
  case class MasterElectionEnd(status: MasterState)
}

class Master(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) extends Actor with ActorLogging {
  import MasterProtocol._
  import ZKOperations._
  import context.dispatcher

  var state: MasterState = MasterStates.Running

  val workerOrganizer = context.actorOf(WorkerOrganizer.props(zookeeperSession), "WorkerOrganizer")

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
    case CreateFailure(e, _, _) if e.code() == Code.NODEEXISTS => {
      state = MasterStates.NotElected
      log.info(s"I'm not the leader $serverId")
      supervisor ! MasterElectionEnd(state)
      context.become(masterExists)
      self ! MasterExists
    }
    case CreateFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => {
      context.become(checkMaster)
      self ! CheckMaster
    }
    case CreateFailure(e, _, _) => throw e
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
    case GetDataFailure(e, _, _) if e.code() == Code.NONODE => {
      context.become(runForMaster)
      self ! RunForMaster
    }
    case GetDataFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => {
      context.system.scheduler.scheduleOnce(1 second, self, CheckMaster)
    }
    case GetDataFailure(e, _, _) => throw e
  }

  def masterExists: Receive = {
    case MasterExists => zookeeperSession ! Exists("/master", watch = true)
    case DoesExist(path, stat, _) =>
    case ExistsFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => {
      self ! MasterExists
    }
    case ExistsFailure(e, _, _) if e.code() == Code.NONODE => {
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
      workerOrganizer ! WorkerOrganizerProtocol.ReassignAndSet(children)
      workerOrganizer ! WorkerOrganizerProtocol.GetTasks
    }
    case GetChildrenFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! GetWorkers
    case GetChildrenFailure(e, _, _) => throw e
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeChildrenChanged => {
      assert("/workers" == e.getPath)
      self ! GetWorkers
    }
    case GetTasks => workerOrganizer ! WorkerOrganizerProtocol.GetTasks
  }

}

object Master {
  def props(serverId: String, zookeeperSession: ActorRef, supervisor: ActorRef) = Props(new Master(serverId, zookeeperSession, supervisor))
}

object WorkerOrganizerProtocol {
  case class ReassignAndSet(workers: List[String])
  case object GetTasks
}

class WorkerOrganizer(zookeeperSession: ActorRef) extends Actor with ActorLogging {
  import WorkerOrganizerProtocol._

  var workersCache = ChildrenCache()
  var tasksCache = ChildrenCache()
  var toProcess = ChildrenCache()

  val random = new Random(this.hashCode())

  val workersTaskReassigner = context.actorOf(WorkersTaskReassigner.props(zookeeperSession), "WorkersTaskReassigner")

  val workerTasksGetter = context.actorOf(WorkerTasksGetter.props(zookeeperSession, self), "WorkerTasksGetter")

  def receive: Receive = {
    case ReassignAndSet(children) => {
      if (workersCache.isEmpty) {
        workersCache = ChildrenCache(children)
        toProcess = ChildrenCache()
      } else {
        log.info("Removing and setting")
        toProcess = workersCache.diff(ChildrenCache(children))
        workersCache = ChildrenCache(children)
        workersTaskReassigner ! WorkersTaskAssignerProtocol.GetAbsentWorkerTasks(toProcess.children)
      }
    }
    case GetTasks => workerTasksGetter ! WorkerTasksGetterProtocol.GetTasks
    case WorkerTasksGetterProtocol.Tasks(children) => {
      if (tasksCache.isEmpty) {
        tasksCache = ChildrenCache(children)
        toProcess = ChildrenCache(children)
      } else {
        toProcess = ChildrenCache(children).diff(tasksCache)
        tasksCache = ChildrenCache(children)
      }
      if (!workersCache.isEmpty) {
        toProcess.children.foreach { task =>
          val workerTaskAssigner = context.actorOf(WorkerTaskAssigner.props(task, workersCache.children(random.nextInt(workersCache.children.length)), zookeeperSession))
          workerTaskAssigner ! WorkerTaskAssignerProtocol.GetTaskData
        }
      }
    }
  }
}

object WorkerOrganizer {
  def props(zookeeperSession: ActorRef): Props = Props(new WorkerOrganizer(zookeeperSession))
}

object WorkerTasksGetterProtocol {
  case object GetTasks
  case class Tasks(tasks: List[String])
}

class WorkerTasksGetter(zookeeperSession: ActorRef, replyTo: ActorRef) extends Actor {
  import WorkerTasksGetterProtocol._
  import ZKOperations._

  def receive: Receive = {
    case GetTasks => zookeeperSession ! GetChildren("/tasks", watch = true)
    case ChildrenGot(path, children, _) => replyTo ! Tasks(children)
    case GetChildrenFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! GetTasks
    case GetChildrenFailure(e, _, _) => throw e
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeChildrenChanged => self ! GetTasks
  }
}

object WorkerTasksGetter {
  def props(zookeeperSession: ActorRef, replyTo: ActorRef): Props = Props(new WorkerTasksGetter(zookeeperSession, replyTo))
}

object WorkersTaskAssignerProtocol {
  case class GetAbsentWorkerTasks(workers: List[String])
}

class WorkersTaskReassigner(zookeeperSession: ActorRef) extends Actor with ActorLogging {
  import WorkersTaskAssignerProtocol._
  import ZKOperations._

  def receive: Receive = {
    case GetAbsentWorkerTasks(workers) => {
      workers.foreach { worker =>
        zookeeperSession ! GetChildren(s"/assign/$worker")
      }
    }
    case ChildrenGot(path, children, _) => {
      log.info(s"Succesfully got a list of assignments: ${children.size} tasks")
      children.foreach { task =>
        context.actorOf(WorkerTaskReassigner.props(task, zookeeperSession)) ! WorkerTaskReassignerProtocol.GetDataReassign(path)
      }
    }
    case GetChildrenFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! GetChildren(e.getPath)
    case GetChildrenFailure(e, _, _) => throw e
  }
}

object WorkersTaskReassigner {
  def props(zookeeperSession: ActorRef): Props = Props(new WorkersTaskReassigner(zookeeperSession))
}

case class RecreateTaskCtx(path: String, task: String, data: Array[Byte])

object WorkerTaskReassignerProtocol {
  case class GetDataReassign(path: String)
  case object RecreateTask
  case object DeleteAssignment
}

class WorkerTaskReassigner(task: String, zookeeperSession: ActorRef) extends Actor with ActorLogging {
  import WorkerTaskReassignerProtocol._
  import ZKOperations._
  import context.dispatcher

  def receive: Receive = {
    case GetDataReassign(path) => zookeeperSession ! GetData(s"$path/$task")
    case DataGot(path, data, stat, _) => {
      context.become(recreateTask(RecreateTaskCtx(path, task, data)))
      self ! RecreateTask
    }
    case GetDataFailure(e, path, _) if e.code() == Code.CONNECTIONLOSS => zookeeperSession ! GetData(s"$path/$task")
    case GetDataFailure(e, _, _) => throw e
  }

  def recreateTask(ctx: RecreateTaskCtx): Receive = {
    case RecreateTask => zookeeperSession ! Create(s"/tasks/$task", ctx.data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
    case Created(path, name, _) => {
      context.become(deleteAssignment(ctx.path))
      self ! DeleteAssignment
    }
    case CreateFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! RecreateTask
    case CreateFailure(e, path, _) if e.code() == Code.NODEEXISTS => {
      log.info("Node exists already, but if it hasn't been deleted, then it will eventually, so we keep trying: " + path)
      context.system.scheduler.scheduleOnce(1 second, self, RecreateTask)
    }
    case CreateFailure(e, _, _) => throw e
  }

  def deleteAssignment(path: String): Receive = {
    case DeleteAssignment => zookeeperSession ! Delete(path, -1)
    case Deleted(path, _) => {
      log.info("Task correctly deleted: {}", path)
      context.stop(self)
    }
    case DeleteFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! DeleteAssignment
    case DeleteFailure(e, _, _) => throw e
  }
}

object WorkerTaskReassigner {
  def props(task: String, zookeeperSession: ActorRef): Props = Props(new WorkerTaskReassigner(task, zookeeperSession))
}

object WorkerTaskAssignerProtocol {
  case object GetTaskData
  case class CreateAssignment(path: String, data: Array[Byte])
}

class WorkerTaskAssigner(task: String, designatedWorker: String, zookeeperSession: ActorRef) extends Actor with ActorLogging {
  import WorkerTaskAssignerProtocol._
  import ZKOperations._

  def receive: Receive = {
    case GetTaskData => zookeeperSession ! GetData(s"/tasks/$task", ctx = task)
    case DataGot(path, data, stat, task: String) =>
      val assignmentPath = s"/assign/$designatedWorker/$task"
      log.info("Assignment path: {}", assignmentPath)
      context.become(createAssignment)
      self ! CreateAssignment(assignmentPath, data)
    case GetDataFailure(e, _, task: String) if e.code() == Code.CONNECTIONLOSS => self ! GetTaskData
    case GetDataFailure(e, _, _) => throw e
  }

  def createAssignment: Receive = {
    case CreateAssignment(path, data) => zookeeperSession ! Create(path, data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, data)
    case Created(path, name, ctx: Array[Byte]) => {
      log.info("Task assigned correctly: {}", name)
      finishAssignment()
    }
    case CreateFailure(e, path, data: Array[Byte]) if e.code() == Code.CONNECTIONLOSS => self ! CreateAssignment(path, data)
    case CreateFailure(e, _, _) if e.code() == Code.NODEEXISTS => {
      log.warning("Task already assigned")
      finishAssignment()
    }
    case CreateFailure(e, _, _) => throw e
  }

  def finishAssignment() = context.stop(self)
}

object WorkerTaskAssigner {
  def props(task: String, designatedWorker: String, zookeeperSession: ActorRef): Props = Props(new WorkerTaskAssigner(task, designatedWorker, zookeeperSession))
}