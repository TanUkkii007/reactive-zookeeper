# Reactive Zookeeper
Let-it-crash style ZooKeeper client based on Akka actor

## Why Actor?

- Asynchronous: ZooKeeper event callback is asynchronously executed and should never block in its execution.
Actors are also asynchronous and well-designed actor never blocks.
- Passive: ZooKeeper client application is basically designed in passive fashion. 
Actors are also passive. They wait for messages and until they receive a message nothing happens.
 This characteristic fits in ZooKeeper's event-driven style.
- State Machine: When a ZooKeeper event is fired, application's state is changed accordingly.
Actors are state machines. They can change its state and behavior in thread-safe manner.
- Fault Tolerant: ZooKeeper is distributed system so failures are inevitable. 
Actor can confine fault, crush safely, and escalate faults it cannot handle to its supervisor.
- Graceful Recovery: When you use ZooKeeper operations, you must retry on failures such as connection loss.
Akka scheduler, backoff supervision and circuit breaker can help recover gracefully.

## Example

A [example project](/example/src/main/scala/tanukkii/reactivezk/example/zookeeperbook) 
shows how to use reactive-zookeepr by building a example application from [ZooKeeper O'Reilly book](https://github.com/fpj/zookeeper-book-example).

## Install

```
resolvers += Resolver.bintrayRepo("tanukkii007", "maven")

libraryDependencies += "github.com/TanUkkii007" % "reactivezookeeper_2.11" % "0.0.2"
```

## Usage

The following import is assumed.

```scala
import tanukkii.reactivezk._
```

### ZooKeeper Session

ZooKeeper session is represented as an actor.

You can gain a singleton session `ActorRef` via

```
ReactiveZK(system).zookeeperSession
```

### ZooKeeper Operation

Each ZooKeeper operation is represented as a message and its result is also a message.
The result of operation have two types of message. One is successful message, 
and the other is failure message with suffix "Failure".

These message can be sent to the ZooKeeper session `ActorRef`. 
The result response message will be returned to the `sender`.

In the following document a `zookeeperSession` variable is assumed to be `ActorRef` representing ZooKeeper session. 

`GetData`, `Exists`, `GetChildren` message support *watch* feature. They have a `watch` flag parameter.
When it is a `true`, a subsequent change event will be sent to its `sender` as `ZooKeeperWatchEvent`.

```scala
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
```

### ZooKeeper#create

`ZKOperations.Create` has following signature. Its fields are corresponding to `ZooKeeper#create` API.
if `ctx` is not specified `NoContext` is used.

```scala
case class Create(path: String, data: Array[Byte], acl: List[ACL], createMode: CreateMode, ctx: Any = NoContext)
```

The `Create` has successful response `Created` and failed response `CreateFailure`.
The `ctx: Any` parameter value is the value specified at `Create` message.
The `error: KeeperException` parameter in `CreateFailure` is the same type provided by ZooKeeper Java API.

```scala
case class Created(path: String, name: String, ctx: Any) extends CreateResponse
case class CreateFailure(error: KeeperException, path: String, ctx: Any) extends CreateResponse
```

A usage example is below.

```scala
  def receive: Receive = {
    case CreateExample(path: String, data: Array[Byte]) => {
      zookeeperSession ! Create(path, data, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL, data)
    }
    case Created(path, name, ctx) => 
    case CreateFailure(e, path, ctx) if e.code() == Code.NODEEXISTS =>
    case CreateFailure(e, path, ctx: Array[Byte]) if e.code() == Code.CONNECTIONLOSS =>
      self ! CreateExample(path, data)
    case CreateFailure(e, _, _) => throw e
  }
```

### ZooKeeper#getData

`ZKOperations.GetData` has following signature. Its fields are corresponding to `ZooKeeper#getData` API.
if `ctx` is not specified `NoContext` is used.

```scala
case class GetData(path: String, watch: Boolean = false, ctx: Any = NoContext)
```

The `GetData` has successful response `DataGot` and failed response `GetDataFailure`.
The `ctx: Any` parameter value is the value specified at `GetData` message.
The `error: KeeperException` parameter in `GetDataFailure` is the same type provided by ZooKeeper Java API.

```scala
case class DataGot(path: String, data: Array[Byte], stat: Stat, ctx: Any) extends GetDataResponse
case class GetDataFailure(error: KeeperException, path: String, ctx: Any) extends GetDataResponse
```

When `watch = true` in the `GetData` is specified, `ZooKeeperWatchEvent(e: WatchedEvent)` will be sent to its `sender`.

A usage example is below.

```scala
  def receive: Receive = {
    case GetDataExample => zookeeperSession ! GetData("/example")
    case DataGot(path, data, stat, _) => 
    case GetDataFailure(e, path, _) if e.code() == Code.NONODE =>
    case GetDataFailure(e, path, _) if e.code() == Code.CONNECTIONLOSS =>
      self ! GetDataExample
    case GetDataFailure(e, _, _) => throw e
  }
```

### ZooKeeper#setData

`ZKOperations.SetData` has following signature. Its fields are corresponding to `ZooKeeper#setData` API.
if `ctx` is not specified `NoContext` is used.

```scala
case class SetData(path: String, data: Array[Byte], version: Int, ctx: Any = NoContext)
```

The `SetData` has successful response `DataSet` and failed response `SetDataFailure`.
The `ctx: Any` parameter value is the value specified at `SetData` message.
The `error: KeeperException` parameter in `SetDataFailure` is the same type provided by ZooKeeper Java API.

```scala
case class DataSet(path: String, stat: Stat, ctx: Any) extends SetDataResponse
case class SetDataFailure(error: KeeperException, path: String, ctx: Any) extends SetDataResponse
```

A usage example is below.

```scala
  def setStatus: Receive = {
    case SetDataExample(data: Array[Byte]) =>
      zookeeperSession ! SetData(SetDataExample, data, -1, data)
    case DataSet(path, stat, _) => 
    case SetDataFailure(e, path, data: Array[Byte]) if e.code() == Code.CONNECTIONLOSS => self ! SetDataExample(path, data)
  }
```

### ZooKeeper#exists

`ZKOperations.Exists` has following signature. Its fields are corresponding to `ZooKeeper#exists` API.
if `ctx` is not specified `NoContext` is used.

```scala
case class Exists(path: String, watch: Boolean = false, ctx: Any = NoContext)
```

The `Exists` has successful response `DoesExist` and failed response `ExistsFailure`.
The `ctx: Any` parameter value is the value specified at `Exists` message.
Note that `stat: Option[Stat]` in `DoesExist` response has type `Option[Stat]`.
The `error: KeeperException` parameter in `ExistsFailure` is the same type provided by ZooKeeper Java API.

```scala
case class DoesExist(path: String, stat: Option[Stat], ctx: Any) extends ExistsResponse
case class ExistsFailure(error: KeeperException, path: String, ctx: Any) extends ExistsResponse
```

When `watch = true` in the `Exists` is specified, `ZooKeeperWatchEvent(e: WatchedEvent)` will be sent to its `sender`.

A usage example is below.

```scala
  def receive: Receive = {
    case ExistsExample => zookeeperSession ! Exists("/example", watch = true)
    case DoesExist(path, stat, _) =>
    case ExistsFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! ExistsExample
    case ExistsFailure(e, _, _) if e.code() == Code.NONODE =>
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeDeleted =>
  }
```

### ZooKeeper#getChildren

`ZKOperations.GetChildren` has following signature. Its fields are corresponding to `ZooKeeper#getChildren` API.
if `ctx` is not specified `NoContext` is used.

```scala
case class GetChildren(path: String, watch: Boolean = false, ctx: Any = NoContext)
```

The `GetChildren` has successful response `ChildrenGot` and failed response `GetChildrenFailure`.
The `ctx: Any` parameter value is the value specified at `GetChildren` message.
The `error: KeeperException` parameter in `GetChildrenFailure` is the same type provided by ZooKeeper Java API.

```scala
case class ChildrenGot(path: String, children: List[String], ctx: Any)
case class GetChildrenFailure(error: KeeperException, path: String, ctx: Any)
```

When `watch = true` in the `GetChildren` is specified, `ZooKeeperWatchEvent(e: WatchedEvent)` will be sent to its `sender`.

A usage example is below.

```scala
  def receive: Receive = {
    case GetChildrenExample => zookeeperSession ! GetChildren("/examples", watch = true)
    case ChildrenGot(path, children, _) =>
    case GetChildrenFailure(e, _, _) if e.code() == Code.CONNECTIONLOSS => self ! GetChildrenExample
    case GetChildrenFailure(e, _, _) => throw e
    case ZooKeeperWatchEvent(e) if e.getType == EventType.NodeChildrenChanged => self ! GetChildrenExample
  }
```

### ZooKeeper#delete

`ZKOperations.Delete` has following signature. Its fields are corresponding to `ZooKeeper#delete` API.
if `ctx` is not specified `NoContext` is used.

```scala
case class Delete(path: String, version: Int, ctx: Any = NoContext)
```

The `Delete` has successful response `Deleted` and failed response `DeleteFailure`.
The `ctx: Any` parameter value is the value specified at `Delete` message.
The `error: KeeperException` parameter in `DeleteFailure` is the same type provided by ZooKeeper Java API.

```scala
case class Deleted(path: String, ctx: Any) extends DeleteResponse
case class DeleteFailure(error: KeeperException, path: String, ctx: Any) extends DeleteResponse
```

A usage example is below.

```scala
  def receive: Receive = {
    case DeleteExample(path) => zookeeperSession ! Delete(path, -1, path)
    case Deleted(path, _) =>
    case DeleteFailure(e, _, path: String) if e.code() == Code.CONNECTIONLOSS => self ! DeleteExample(path)
    case DeleteFailure(e, _, _) => throw e
  }
```


## Configuration
See [reference.conf](/reactive-zookeeper/src/main/resources/reference.conf).

Default values are follow.

```
reactive-zookeeper {
  connect-string = "localhost:2181"
  session-timeout = 5000
}
```

## Ordering

As you know ZooKeeper ensure the order of operation and event.
When you use ZooKeeper with Actor, you must be careful with ordering.
Actor is a concurrent unit. Within an actor the order of message passing to a specific receiver is preserved.
However when multiple actors are sending to Zookeeper session actor, no one knows the order of the messages reception.