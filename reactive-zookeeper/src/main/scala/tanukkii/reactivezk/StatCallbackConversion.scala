package tanukkii.reactivezk

import org.apache.zookeeper.AsyncCallback.StatCallback
import org.apache.zookeeper.data.Stat

trait StatCallbackConversion {
  implicit def toStatCallback[Ctx](f: (Int, String, Ctx, Stat) => Unit): StatCallback = new StatCallback {
    def processResult(rc: Int, path: String, ctx: Any, stat: Stat): Unit = f(rc, path, ctx.asInstanceOf[Ctx], stat)
  }
}

object StatCallbackConversion extends StatCallbackConversion