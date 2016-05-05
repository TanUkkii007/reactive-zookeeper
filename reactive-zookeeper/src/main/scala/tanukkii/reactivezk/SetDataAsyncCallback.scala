package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.data.Stat
import tanukkii.reactivezk.ZKOperations.{SetDataFailure, DataSet}

trait SetDataAsyncCallback {
  import KeeperExceptionConverter._

  def setDataAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope, Stat) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope, stat: Stat) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx.sender ! DataSet(path, stat, ctx.originalCtx)
      case Some(e) => ctx.sender ! SetDataFailure(e, path, ctx.originalCtx)
    }
  }
}
