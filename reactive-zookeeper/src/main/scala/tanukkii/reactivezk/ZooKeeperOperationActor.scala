package tanukkii.reactivezk

import akka.actor.{ActorRef, Props, Actor}
import org.apache.zookeeper.{KeeperException, CreateMode, ZooKeeper}
import org.apache.zookeeper.data.{Stat, ACL}
import scala.collection.JavaConverters._

sealed trait NoContext
case object NoContext extends NoContext

private [reactivezk] case class ContextEnvelope(sender: ActorRef, originalCtx: Any)

object ZKOperations {
  case class Create(path: String, data: Array[Byte], acl: List[ACL], createMode: CreateMode, ctx: Any = NoContext)
  sealed trait CreateResponse
  case class Created(path: String, name: String, ctx: Any) extends CreateResponse
  case class CreateFailure(error: KeeperException, path: String, ctx: Any) extends CreateResponse

  case class GetData(path: String, watch: Boolean = false, ctx: Any = NoContext)
  sealed trait GetDataResponse
  case class DataGot(path: String, data: Array[Byte], stat: Stat, ctx: Any) extends GetDataResponse
  case class GetDataFailure(error: KeeperException, path: String, ctx: Any) extends GetDataResponse

  case class SetData(path: String, data: Array[Byte], version: Int, ctx: Any = NoContext)
  sealed trait SetDataResponse
  case class DataSet(path: String, stat: Stat, ctx: Any) extends SetDataResponse
  case class SetDataFailure(error: KeeperException, path: String, ctx: Any) extends SetDataResponse

  case class Exists(path: String, watch: Boolean = false, ctx: Any = NoContext)
  sealed trait ExistsResponse
  case class DoesExist(path: String, stat: Option[Stat], ctx: Any) extends ExistsResponse
  case class ExistsFailure(error: KeeperException, path: String, ctx: Any) extends ExistsResponse

  case class GetChildren(path: String, watch: Boolean = false, ctx: Any = NoContext)
  sealed trait GetChildrenResponse
  case class ChildrenGot(path: String, children: List[String], ctx: Any)
  case class GetChildrenFailure(error: KeeperException, path: String, ctx: Any)

  case class Delete(path: String, version: Int, ctx: Any = NoContext)
  sealed trait DeleteResponse
  case class Deleted(path: String, ctx: Any) extends DeleteResponse
  case class DeleteFailure(error: KeeperException, path: String, ctx: Any) extends DeleteResponse
}

private [reactivezk] class ZooKeeperOperationActor(zookeeper: ZooKeeper) extends Actor
with CreateAsyncCallback with GetDataAsyncCallback with SetDataAsyncCallback with ExistsAsyncCallback with GetChildrenAsyncCallback with DeleteAsyncCallback with WatcherCallback {
  import CallbackConversion._
  import ZKOperations._

  def receive: Receive = {
    case Create(path, data, acl, createMode, ctx) => zookeeper.create(path, data, acl.asJava, createMode, createAsyncCallback, ContextEnvelope(sender(), ctx))
    case GetData(path, watch, ctx) if !watch => zookeeper.getData(path, watch, getDataAsyncCallback, ContextEnvelope(sender(), ctx))
    case GetData(path, watch, ctx) if watch => zookeeper.getData(path, watchCallback(sender()), getDataAsyncCallback, ContextEnvelope(sender(), ctx))
    case SetData(path, data, version, ctx) => zookeeper.setData(path, data, version, setDataAsyncCallback, ContextEnvelope(sender(), ctx))
    case Exists(path, watch, ctx) if !watch => zookeeper.exists(path, watch, existsAsyncCallback, ContextEnvelope(sender(), ctx))
    case Exists(path, watch, ctx) if watch => zookeeper.exists(path, watchCallback(sender()), existsAsyncCallback, ContextEnvelope(sender(), ctx))
    case GetChildren(path, watch, ctx) if !watch => zookeeper.getChildren(path, watch, getChildrenAsyncCallback, ContextEnvelope(sender(), ctx))
    case GetChildren(path, watch, ctx) if watch => zookeeper.getChildren(path, watchCallback(sender()), getChildrenAsyncCallback, ContextEnvelope(sender(), ctx))
    case Delete(path, version, ctx) => zookeeper.delete(path, version, deleteAsyncCallback, ContextEnvelope(sender(), ctx))
  }

}

private [reactivezk] object ZooKeeperOperationActor {
  def props(zookeeper: ZooKeeper): Props = Props(new ZooKeeperOperationActor(zookeeper))
}