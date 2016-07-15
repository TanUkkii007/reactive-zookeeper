package tanukkii.reactivezk

import java.io.{ByteArrayInputStream, File}
import org.apache.commons.io.FileUtils
import org.apache.zookeeper.server.ServerConfig
import org.apache.zookeeper.server.quorum.QuorumPeerConfig
import org.scalatest.{BeforeAndAfterAll, Suite}

trait ZooKeeperTest extends Suite with BeforeAndAfterAll {

  val server = new TestingZooKeeperMain()

  val zooKeeperConfigString: String

  private var dataDir: String = _

  override protected def beforeAll(): Unit = {
    new Thread(new Runnable {
      override def run(): Unit = {
        val config = new ServerConfig()
        val peerConfig = new QuorumPeerConfig()
        val props = new java.util.Properties()
        props.load(new ByteArrayInputStream(zooKeeperConfigString.getBytes))
        dataDir = props.getProperty("dataDir")
        peerConfig.parseProperties(props)
        config.readFrom(peerConfig)
        server.runFromConfig(config)
      }
    }).start()
    Thread.sleep(8000)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    server.close()
    FileUtils.deleteDirectory(new File(dataDir))
    super.afterAll()
  }
}
