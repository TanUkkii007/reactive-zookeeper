package tanukkii.reactivezk

import org.apache.zookeeper.AsyncCallback.ChildrenCallback
import scala.collection.JavaConverters._

trait ChildrenCallbackConversion {
  implicit def toChildrenCallback[Ctx](f: (Int, String, Ctx, List[String]) => Unit): ChildrenCallback = new ChildrenCallback {
    def processResult(rc: Int, path: String, ctx: Any, children: java.util.List[String]): Unit = f(rc, path, ctx.asInstanceOf[Ctx], children.asScala.toList)
  }
}

object ChildrenCallbackConversion extends ChildrenCallbackConversion