package tanukkii.reactivezk

import java.io.{IOException, File}
import com.google.common.io.Closeables
import org.apache.curator.test.TestingServer
import org.scalatest.{BeforeAndAfterAll, Suite}

trait ZooKeeperTest extends Suite with BeforeAndAfterAll {

  var testingServer: TestingServer = _

  val dataDir: String

  def zkConnectString: String = testingServer.getConnectString

  def zkPort: Int = testingServer.getPort

  override protected def beforeAll(): Unit = {
    testingServer = new TestingServer(-1, new File(dataDir))
    testingServer.start()
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    try {
      Closeables.close(testingServer, true)
    } catch {
      case e: IOException =>
    }
    super.afterAll()
  }
}
