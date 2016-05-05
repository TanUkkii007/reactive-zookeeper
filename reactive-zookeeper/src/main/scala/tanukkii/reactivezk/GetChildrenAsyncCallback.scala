package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import tanukkii.reactivezk.ZKOperations.{GetChildrenFailure, ChildrenGot}


trait GetChildrenAsyncCallback {
  import KeeperExceptionConverter._

  def getChildrenAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope, List[String]) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope, children: List[String]) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx.sender ! ChildrenGot(path, children, ctx.originalCtx)
      case Some(e) => ctx.sender ! GetChildrenFailure(e, ctx.originalCtx)
    }
  }
}