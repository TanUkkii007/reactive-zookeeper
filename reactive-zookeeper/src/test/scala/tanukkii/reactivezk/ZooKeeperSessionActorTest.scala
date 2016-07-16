package tanukkii.reactivezk

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.apache.zookeeper.CreateMode
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

      zooKeeperActor ! ZKOperations.Create("/test-getdata", "test data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT)

      expectMsg(ZKOperations.Created("/test-getdata", "/test-getdata", NoContext))

      zooKeeperActor ! ZKOperations.GetData("/test-getdata")

      val result = expectMsgType[ZKOperations.DataGot]
      result.path should be("/test-getdata")
      result.data should be("test data".getBytes())
    }
  }
}
