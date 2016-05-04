package tanukkii.reactivezk

import org.apache.zookeeper.AsyncCallback.DataCallback
import org.apache.zookeeper.data.Stat

trait DataCallbackConversion {
  implicit def toDataCallback[Ctx](f: (Int, String, Ctx, Array[Byte], Stat) => Unit): DataCallback = new DataCallback {
    def processResult(rc: Int, path: String, ctx: Any, data: Array[Byte], stat: Stat): Unit = f(rc, path, ctx.asInstanceOf[Ctx], data, stat)
  }
}

object DataCallbackConversion extends DataCallbackConversion