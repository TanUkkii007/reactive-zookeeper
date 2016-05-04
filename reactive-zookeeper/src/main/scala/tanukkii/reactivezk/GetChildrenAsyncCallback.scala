package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import tanukkii.reactivezk.ZKOperations.{GetChildrenFailure, ChildrenGot}


trait GetChildrenAsyncCallback {
  import KeeperExceptionConverter._

  def getChildrenAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ActorRef, List[String]) => Unit = {
    (rc: Int, path: String, ctx: ActorRef, children: List[String]) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx ! ChildrenGot(path, children)
      case Some(e) => ctx ! GetChildrenFailure(e)
    }
  }
}