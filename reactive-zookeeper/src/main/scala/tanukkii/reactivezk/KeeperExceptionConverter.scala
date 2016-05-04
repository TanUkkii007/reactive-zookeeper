package tanukkii.reactivezk

import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.KeeperException.Code

trait KeeperExceptionConverter {
  implicit class ToKeeperException(code: Int) {
    def toKeeperExceptionOpt(path: String): Option[KeeperException] = {
      if (Code.get(code) == Code.OK) None
      else Some(KeeperException.create(Code.get(code), path))
    }
  }
}

object KeeperExceptionConverter extends KeeperExceptionConverter