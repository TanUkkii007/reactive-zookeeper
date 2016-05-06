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

case class TaskObject(task: String, taskName: Option[String] = None, done: Boolean = false, successful: Boolean = false)

object ClientProtocol {
  case class SubmitTask(taskObject: TaskObject)
}

class Client(zookeeperSession: ActorRef) extends Actor {
  import ClientProtocol._

  def receive: Receive = {
    case SubmitTask(taskObject) => context.actorOf(Task.props(zookeeperSession)) forward TaskProtocol.Submit(taskObject)
  }
}

object Client {
  def props(zookeeperSession: ActorRef): Props = Props(new Client(zookeeperSession))
}

object TaskProtocol {
  case class Submit(taskObject: TaskObject)
  case class WatchStatus(path: String)
}

class Task(zookeeperSession: ActorRef) extends Actor with ActorLogging {
  import TaskProtocol._

  var taskObject: TaskObject = _

  def receive: Receive = {
    case Submit(taskObj) => {
      taskObject = taskObj
      zookeeperSession ! Create("/tasks/task-", taskObject.task.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL)
    }
    case Created(path, name, _) => {
      log.info("My created task name: {}", name)
      taskObject = taskObject.copy(taskName = Some(name))
      val path = name.replace("/tasks/", "/status/")
      context.become(watchStatus(path))
      self ! WatchStatus(path)
    }
    case CreateFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! Submit
    case CreateFailure(e, _, _) => throw e
  }

  def watchStatus(path: String): Receive = {
    case WatchStatus(_) => zookeeperSession ! Exists(path, watch = true)
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeCreated => {
      assert(e.getPath.contains("/status/task-"))
      assert(path == e.getPath)
      context.become(getData)
      zookeeperSession ! GetData(path)
    }
    case DoesExist(path, stat, _) => {
      stat.foreach { _ =>
        context.become(getData)
        zookeeperSession ! GetData(path)
        log.info("Status node is there: {}", path)
      }
    }
    case ExistsFailure(e, _, _) if e.code() == Code.NONODE =>
    case ExistsFailure(e, _, _) => throw e
  }

  def getData: Receive = {
    case DataGot(path, data, stat, _) => {
      val taskResult = new String(data)
      log.info("Task {}, {}", path, taskResult)
      context.become(taskDelete)
      zookeeperSession ! Delete(path, -1)
    }
    case GetDataFailure(e, path, _) if e.code() == Code.CONNECTIONLOSS => zookeeperSession ! GetData(path)
    case GetDataFailure(e, _, _) if e.code() == Code.NONODE => {
      log.warning("Status node is gone!")
      finishTask()
    }
  }

  def taskDelete: Receive = {
    case Deleted(path, _) => {
      log.info("Successfully deleted {}", path)
      finishTask()
    }
    case DeleteFailure(e, path, _) if e.code() == Code.CONNECTIONLOSS => zookeeperSession ! Delete(path, -1)
    case DeleteFailure(e, _, _) => throw e
  }

  def finishTask() = context.stop(self)
}

object Task {
  def props(zookeeperSession: ActorRef): Props = Props(new Task(zookeeperSession))
}