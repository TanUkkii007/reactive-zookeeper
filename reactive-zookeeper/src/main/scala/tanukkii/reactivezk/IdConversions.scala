package tanukkii.reactivezk

import java.util
import org.apache.zookeeper.data.ACL
import scala.collection.JavaConverters._

trait IdConversions {
  implicit def toACLList(acls: util.ArrayList[ACL]): List[ACL] = acls.asScala.toList
}

object IdConversions extends IdConversions