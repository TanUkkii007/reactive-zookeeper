package tanukkii.reactivezk

import org.apache.zookeeper.AsyncCallback.MultiCallback
import org.apache.zookeeper.OpResult
import scala.collection.JavaConverters._

trait MultiCallbackConversion {
  implicit def toMultiCallback[Ctx](f: (Int, String, Ctx, Seq[OpResult]) => Unit): MultiCallback = new MultiCallback {
    override def processResult(rc: Int, path: String, ctx: Any, opResults: java.util.List[OpResult]): Unit = {
      f(rc, path, ctx.asInstanceOf[Ctx], opResults.asScala)
    }
  }
}
