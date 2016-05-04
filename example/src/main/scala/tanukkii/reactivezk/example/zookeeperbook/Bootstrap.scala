package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.{Props, ActorLogging, ActorRef, Actor}
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.Code
import org.apache.zookeeper.ZooDefs.Ids
import tanukkii.reactivezk.IdConversions
import tanukkii.reactivezk.ZKOperations.{CreateFailure, Created, Create}
import IdConversions._

object BootstrapProtocol {
  case object CreateParents
  case class CreateParent(path: String, data: Array[Byte])
  case object ParentCreated
}

class Bootstrap(zooKeeperSession: ActorRef, supervisor: ActorRef) extends Actor with ActorLogging {
  import BootstrapProtocol._

  val paths = Set("/workers", "/assign", "/tasks", "/status")

  var createdPaths: Set[String] = Set.empty

  def receive: Receive = {
    case CreateParents => {
      context.become(createParent)
      paths.foreach { path =>
        self ! CreateParent(path, Array.empty)
      }
    }
  }

  def createParent: Receive = {
    case CreateParent(path, data) => zooKeeperSession ! createParentMessage(path, Array.empty)
    case Created(path, name) => {
      createdPaths += path
      log.info("Parent created at {}", path)
      if (createdPaths == paths) supervisor ! ParentCreated
    }
    case CreateFailure(e) if e.code() == Code.CONNECTIONLOSS => self ! CreateParent(e.getPath, Array.empty)
    case CreateFailure(e) if e.code() == Code.NODEEXISTS => {
      createdPaths += e.getPath
      log.warning("Parent already registered: {}", e.getPath)
      if (createdPaths == paths) supervisor ! ParentCreated
    }
    case CreateFailure(e) => throw e
  }

  def createParentMessage(path: String, data: Array[Byte]) = Create(path, data, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
}

object Bootstrap {
  def props(zooKeeperSession: ActorRef, supervisor: ActorRef) = Props(new Bootstrap(zooKeeperSession, supervisor))
}