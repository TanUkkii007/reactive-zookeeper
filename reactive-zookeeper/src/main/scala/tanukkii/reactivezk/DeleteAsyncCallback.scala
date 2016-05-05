package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import tanukkii.reactivezk.ZKOperations.{DeleteFailure, Deleted}

trait DeleteAsyncCallback {
  import KeeperExceptionConverter._

  def deleteAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx.sender ! Deleted(path, ctx.originalCtx)
      case Some(e) => ctx.sender ! DeleteFailure(e, path, ctx.originalCtx)
    }
  }
}
