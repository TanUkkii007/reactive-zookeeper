package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.OpResult
import tanukkii.reactivezk.ZKOperations.{MultiFailure, MultiResult}

trait MultiAsyncCallback {
  import KeeperExceptionConverter._

  def multiCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope, Seq[OpResult]) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope, opResults: Seq[OpResult]) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx.sender ! MultiResult(opResults)
      case Some(e) => ctx.sender ! MultiFailure(e, path, ctx)
    }
  }
}
