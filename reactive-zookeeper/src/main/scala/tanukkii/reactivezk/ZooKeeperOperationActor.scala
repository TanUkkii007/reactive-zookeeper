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
  case class GetData(path: String, watch: Boolean)
  sealed trait GetDataResponse
  case class GotData(path: String, data: Array[Byte], stat: Stat) extends GetDataResponse
  case class GetDataFailure(error: KeeperException) extends GetDataResponse
}

private [reactivezk] class ZooKeeperOperationActor(zookeeper: ZooKeeper) extends Actor with CreateAsyncCallback with GetDataAsyncCallback {
  import StringCallbackConversion._
  import DataCallbackConversion._
  import ZKOperations._

  def receive: Receive = {
    case Create(path, data, acl, createMode) => zookeeper.create(path, data, acl.asJava, createMode, createAsyncCallback, sender())
    case GetData(path, watch) => zookeeper.getData(path, watch, getDataAsyncCallback, sender())
  }

}

private [reactivezk] object ZooKeeperOperationActor {
  def props(zookeeper: ZooKeeper): Props = Props(new ZooKeeperOperationActor(zookeeper))
}