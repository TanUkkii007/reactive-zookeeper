package tanukkii.reactivezk

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.apache.zookeeper.{KeeperException, CreateMode}
import org.apache.zookeeper.ZooDefs.Ids
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._

class ZooKeeperSessionActorTest extends TestKit(ActorSystem("ZooKeeperSessionActorTest"))
  with WordSpecLike with ZooKeeperTest with Matchers with ImplicitSender with StopSystemAfterAll {

  val dataDir: String = "target/zookeeper/ZooKeeperSessionActorTest"

  "ZooKeeperSessionActor" must {

    "create znode" in {

      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZKOperations.Create("/test-create", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-create", "/test-create", NoContext))
    }

    "get data of znode" in {

      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZKOperations.Create("/test-get-data", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-get-data", "/test-get-data", NoContext))

      zooKeeperActor ! ZKOperations.GetData("/test-get-data")

      val result = expectMsgType[ZKOperations.DataGot]
      result.path should be("/test-get-data")
      result.data should be("test data".getBytes())
    }

    "set data of znode" in {

      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZKOperations.Create("/test-set-data", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-set-data", "/test-set-data", NoContext))

      zooKeeperActor ! ZKOperations.GetData("/test-set-data")

      val result = expectMsgType[ZKOperations.DataGot]
      result.path should be("/test-set-data")
      result.data should be("test data".getBytes())
      result.stat.getVersion should be(0)

      zooKeeperActor ! ZKOperations.SetData("/test-set-data", "modified data".getBytes(), 0)

      expectMsgType[ZKOperations.DataSet].path should be("/test-set-data")

      zooKeeperActor ! ZKOperations.GetData("/test-set-data")

      val modified = expectMsgType[ZKOperations.DataGot]
      modified.path should be("/test-set-data")
      modified.data should be("modified data".getBytes())
      modified.stat.getVersion should be(1)
    }

    "check existence of znode" in {

      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZKOperations.Create("/test-exists", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-exists", "/test-exists", NoContext))

      zooKeeperActor ! ZKOperations.Exists("/test-exists")

      val result = expectMsgType[ZKOperations.DoesExist]
      result.path should be("/test-exists")
      result.stat.isDefined should be(true)
    }

    "get children of znode" in {

      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZKOperations.Create("/test-get-children", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-get-children", "/test-get-children", NoContext))

      0 to 2 foreach { idx =>
        zooKeeperActor ! ZKOperations.Create(s"/test-get-children/$idx", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
      }

      expectMsgAllOf(
        ZKOperations.Created("/test-get-children/0", "/test-get-children/0", NoContext),
        ZKOperations.Created("/test-get-children/1", "/test-get-children/1", NoContext),
        ZKOperations.Created("/test-get-children/2", "/test-get-children/2", NoContext)
      )

      zooKeeperActor ! ZKOperations.GetChildren("/test-get-children")

      val result = expectMsgType[ZKOperations.ChildrenGot]
      result.path should be("/test-get-children")
      result.children should be(List("0", "1", "2"))
    }

    "delete znode" in {

      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZKOperations.Create("/test-delete", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-delete", "/test-delete", NoContext))

      zooKeeperActor ! ZKOperations.GetData("/test-delete")

      val result = expectMsgType[ZKOperations.DataGot]
      result.path should be("/test-delete")
      result.data should be("test data".getBytes())

      zooKeeperActor ! ZKOperations.Delete("/test-delete", 0)

      expectMsg(ZKOperations.Deleted("/test-delete", NoContext))

      zooKeeperActor ! ZKOperations.GetData("/test-delete")

      val deleted = expectMsgType[ZKOperations.GetDataFailure]
      deleted.path should be("/test-delete")
      deleted.error.code() should be(KeeperException.Code.NONODE)
    }

    "restart session" in {
      val zooKeeperActor = system.actorOf(ZooKeeperSessionActor.props(zkConnectString, 10 seconds))

      zooKeeperActor ! ZooKeeperSession.Restart
      expectMsg(ZooKeeperSession.Restarted)

      zooKeeperActor ! ZKOperations.Create("/test-restart", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)
      expectMsg(ZKOperations.Created("/test-restart", "/test-restart", NoContext))
    }
  }
}
