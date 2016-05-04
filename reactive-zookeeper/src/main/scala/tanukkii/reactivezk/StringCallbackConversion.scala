package tanukkii.reactivezk

import org.apache.zookeeper.AsyncCallback.StringCallback

trait StringCallbackConversion {

  implicit def toStringCallback[Ctx](f: (Int, String, Ctx, String) => Unit): StringCallback = new StringCallback {
    def processResult(rc: Int, path: String, ctx: Any, name: String): Unit = f(rc, path, ctx.asInstanceOf[Ctx], name)
  }

}

object StringCallbackConversion extends StringCallbackConversion