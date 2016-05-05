package tanukkii.reactivezk

import org.apache.zookeeper.AsyncCallback.VoidCallback

trait VoidCallbackConversion {
  implicit def toVoidCallback[Ctx](f: (Int, String, Ctx) => Unit): VoidCallback = new VoidCallback {
    def processResult(rc: Int, path: String, ctx: Any): Unit = f(rc, path, ctx.asInstanceOf[Ctx])
  }
}

object VoidCallbackConversion extends VoidCallbackConversion