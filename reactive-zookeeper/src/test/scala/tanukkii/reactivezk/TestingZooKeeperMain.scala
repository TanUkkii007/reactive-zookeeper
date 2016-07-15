package tanukkii.reactivezk

import org.apache.zookeeper.server.ZooKeeperServerMain

class TestingZooKeeperMain extends ZooKeeperServerMain {
  def close(): Unit = shutdown()
}