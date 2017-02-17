package tanukkii.reactivezk

import java.util

import akka.actor.{ Actor, ActorRef, Props }
import org.apache.zookeeper._
import org.apache.zookeeper.data.{ ACL, Stat }

import scala.collection.JavaConverters._

sealed trait NoContext
case object NoContext extends NoContext

private [reactivezk] case class ContextEnvelope(sender: ActorRef, originalCtx: Any)

object ZKOperations {
  sealed trait ZKOperation { val ctx: Any }
  trait Path { self: ZKOperation => val path: String }
  trait Data { self: ZKOperation => val data: Array[Byte] }
  sealed trait ZKCommand extends ZKOperation
  trait Watch { self: ZKCommand => val watch: Boolean }
  trait Version { self: ZKCommand => val version:Int }

  trait GenericZKResponse[+T <: ZKCommand] extends ZKOperation
  // we have to defined these concrete types for type constraint combinations due to type erasure.
  trait ZKResponse extends GenericZKResponse[ZKCommand]
  trait ZKResponseOfCommandWithPath extends GenericZKResponse[ZKCommand with Path] with Path
  trait ZKResponseOfCommandWithPathWatch extends GenericZKResponse[ZKCommand with Path with Watch] with Path
  trait ZKResponseOfCommandWithPathData extends GenericZKResponse[ZKCommand with Path with Data] with Path
  trait ZKResponseOfCommandWithPathVersion extends GenericZKResponse[ZKCommand with Path with Version] with Path
  trait ZKResponseOfCommandWithPathDataVersion extends GenericZKResponse[ZKCommand with Path with Data with Version] with Path

  trait GenericZKCommandFailure[+T <: ZKCommand] extends GenericZKResponse[T] { self: GenericZKResponse[T] => val error: KeeperException }
  // we have to defined these concrete types for type constraint combinations due to type erasure.
  trait ZKCommandFailure extends GenericZKCommandFailure[ZKCommand]
  trait ZKCommandFailureOfCommandWithPath extends ZKResponseOfCommandWithPath
  trait ZKCommandFailureOfCommandWithPathWatch extends ZKResponseOfCommandWithPathWatch
  trait ZKCommandFailureOfCommandWithPathData extends ZKResponseOfCommandWithPathData
  trait ZKCommandFailureOfCommandWithPathVersion extends ZKResponseOfCommandWithPathVersion
  trait ZKCommandFailureOfCommandWithPathDataVersion extends ZKResponseOfCommandWithPathDataVersion

  case class Create(path: String, data: Array[Byte], acl: List[ACL], createMode: CreateMode, ctx: Any = NoContext) extends ZKCommand with Path with Data
  sealed trait CreateResponse extends GenericZKResponse[Create] with ZKResponseOfCommandWithPathData
  case class Created(path: String, name: String, ctx: Any) extends CreateResponse
  case class CreateFailure(error: KeeperException, path: String, ctx: Any) extends CreateResponse with GenericZKCommandFailure[Create] with ZKCommandFailureOfCommandWithPathData

  case class GetData(path: String, watch: Boolean = false, ctx: Any = NoContext) extends ZKCommand with Path with Watch
  sealed trait GetDataResponse extends GenericZKResponse[GetData] with ZKResponseOfCommandWithPathWatch
  case class DataGot(path: String, data: Array[Byte], stat: Stat, ctx: Any) extends GetDataResponse
  case class GetDataFailure(error: KeeperException, path: String, ctx: Any) extends GetDataResponse with GenericZKCommandFailure[GetData] with ZKCommandFailureOfCommandWithPathWatch

  case class SetData(path: String, data: Array[Byte], version: Int, ctx: Any = NoContext) extends ZKCommand with Path with Data with Version
  sealed trait SetDataResponse extends GenericZKResponse[SetData] with ZKResponseOfCommandWithPathDataVersion
  case class DataSet(path: String, stat: Stat, ctx: Any) extends SetDataResponse
  case class SetDataFailure(error: KeeperException, path: String, ctx: Any) extends SetDataResponse with GenericZKCommandFailure[SetData] with ZKCommandFailureOfCommandWithPathDataVersion

  case class Exists(path: String, watch: Boolean = false, ctx: Any = NoContext) extends ZKCommand with Path with Watch
  sealed trait ExistsResponse extends GenericZKResponse[Exists] with ZKResponseOfCommandWithPathWatch
  case class DoesExist(path: String, stat: Option[Stat], ctx: Any) extends ExistsResponse
  case class ExistsFailure(error: KeeperException, path: String, ctx: Any) extends ExistsResponse with GenericZKCommandFailure[Exists] with ZKCommandFailureOfCommandWithPathWatch

  case class GetChildren(path: String, watch: Boolean = false, ctx: Any = NoContext) extends ZKCommand with Path with Watch
  sealed trait GetChildrenResponse extends GenericZKResponse[GetChildren] with ZKResponseOfCommandWithPathWatch
  case class ChildrenGot(path: String, children: List[String], ctx: Any) extends GetChildrenResponse
  case class GetChildrenFailure(error: KeeperException, path: String, ctx: Any) extends GetChildrenResponse with GenericZKCommandFailure[GetChildren] with ZKCommandFailureOfCommandWithPathWatch

  case class Delete(path: String, version: Int, ctx: Any = NoContext) extends ZKCommand with Path with Version
  sealed trait DeleteResponse extends GenericZKResponse[Delete] with ZKResponseOfCommandWithPathVersion
  case class Deleted(path: String, ctx: Any) extends DeleteResponse
  case class DeleteFailure(error: KeeperException, path: String, ctx: Any) extends DeleteResponse with GenericZKCommandFailure[Delete] with ZKCommandFailureOfCommandWithPathVersion

  case class Multi(ops: List[Op], ctx: Any = NoContext) extends ZKCommand
  sealed trait MultiResponse extends GenericZKResponse[Multi]
  case class MultiResult(results: Seq[OpResult], ctx: Any) extends MultiResponse
  case class MultiFailure(error: KeeperException, ctx: Any) extends MultiResponse with GenericZKCommandFailure[Multi]
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
    case Multi(ops, ctx) => try {
      // in order to support ZooKeeper versions <3.4.7 synchronous API is used here
      val opResults: util.List[OpResult] = zookeeper.multi(ops.asJava)
      sender() ! MultiResult(opResults.asScala, ctx)
    } catch {
      case e: KeeperException => sender() ! MultiFailure(e, ctx)
    }
  }

}

private [reactivezk] object ZooKeeperOperationActor {
  def props(zookeeper: ZooKeeper): Props = Props(new ZooKeeperOperationActor(zookeeper))
}