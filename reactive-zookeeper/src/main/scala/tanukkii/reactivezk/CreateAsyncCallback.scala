package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import tanukkii.reactivezk.ZKOperations.{CreateFailure, Created}

trait CreateAsyncCallback {
  import KeeperExceptionConverter._

  def createAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope, String) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope, name: String) => rc.toKeeperExceptionOpt(path) match {
      case Some(e) => ctx.sender ! CreateFailure(e, path, ctx.originalCtx)
      case None => ctx.sender ! Created(path, name, ctx.originalCtx)
    }
  }
}
