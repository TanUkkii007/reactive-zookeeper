package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.data.Stat
import tanukkii.reactivezk.ZKOperations.{ExistsFailure, DoesExist}

trait ExistsAsyncCallback {
  import KeeperExceptionConverter._

  def existsAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope, Stat) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope, stat: Stat) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx.sender ! DoesExist(path, stat, ctx.originalCtx)
      case Some(e) => ctx.sender ! ExistsFailure(e, ctx.originalCtx)
    }
  }
}