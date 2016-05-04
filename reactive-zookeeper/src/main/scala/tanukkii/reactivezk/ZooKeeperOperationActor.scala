package tanukkii.reactivezk

import akka.actor.{Props, Actor}
import org.apache.zookeeper.{KeeperException, CreateMode, ZooKeeper}
import org.apache.zookeeper.data.{Stat, ACL}
import scala.collection.JavaConverters._

object ZKOperations {
  case class Create(path: String, data: Array[Byte], acl: List[ACL], createMode: CreateMode)
  sealed trait CreateResponse
  case class Created(path: String, name: String) extends CreateResponse
  case class CreateFailure(error: KeeperException) extends CreateResponse

  case class GetData(path: String, watch: Boolean = false)
  sealed trait GetDataResponse
  case class DataGot(path: String, data: Array[Byte], stat: Stat) extends GetDataResponse
  case class GetDataFailure(error: KeeperException) extends GetDataResponse

  case class SetData(path: String, data: Array[Byte], version: Int)
  sealed trait SetDataResponse
  case class DataSet(path: String, stat: Stat) extends SetDataResponse
  case class SetDataFailure(error: KeeperException) extends SetDataResponse

  case class Exists(path: String, watch: Boolean = false)
  sealed trait ExistsResponse
  case class DoesExist(path: String, stat: Stat) extends ExistsResponse
  case class ExistsFailure(error: KeeperException) extends ExistsResponse

  case class GetChildren(path: String, watch: Boolean = false)
  sealed trait GetChildrenResponse
  case class ChildrenGot(path: String, children: List[String])
  case class GetChildrenFailure(error: KeeperException)
}

private [reactivezk] class ZooKeeperOperationActor(zookeeper: ZooKeeper) extends Actor
with CreateAsyncCallback with GetDataAsyncCallback with SetDataAsyncCallback with ExistsAsyncCallback with GetChildrenAsyncCallback with WatcherCallback {
  import StringCallbackConversion._
  import DataCallbackConversion._
  import StatCallbackConversion._
  import ChildrenCallbackConversion._
  import WatcherConversion._
  import ZKOperations._

  def receive: Receive = {
    case Create(path, data, acl, createMode) => zookeeper.create(path, data, acl.asJava, createMode, createAsyncCallback, sender())
    case GetData(path, watch) if !watch => zookeeper.getData(path, watch, getDataAsyncCallback, sender())
    case GetData(path, watch) if watch => zookeeper.getData(path, watchCallback(sender()), getDataAsyncCallback, sender())
    case SetData(path, data, version) => zookeeper.setData(path, data, version, setDataAsyncCallback, sender())
    case Exists(path, watch) if !watch => zookeeper.exists(path, watch, existsAsyncCallback, sender())
    case Exists(path, watch) if watch => zookeeper.exists(path, watchCallback(sender()), existsAsyncCallback, sender())
    case GetChildren(path, watch) if !watch => zookeeper.getChildren(path, watch, getChildrenAsyncCallback, sender())
    case GetChildren(path, watch) if watch => zookeeper.getChildren(path, watchCallback(sender()), getChildrenAsyncCallback, sender())
  }

}

private [reactivezk] object ZooKeeperOperationActor {
  def props(zookeeper: ZooKeeper): Props = Props(new ZooKeeperOperationActor(zookeeper))
}