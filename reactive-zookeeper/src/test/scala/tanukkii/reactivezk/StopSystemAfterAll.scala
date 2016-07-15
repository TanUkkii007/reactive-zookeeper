package tanukkii.reactivezk

import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Suite}
import scala.concurrent.Await
import scala.concurrent.duration._


trait StopSystemAfterAll extends BeforeAndAfterAll { self: TestKit with Suite =>
  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 5 seconds)
    super.afterAll()
  }
}
