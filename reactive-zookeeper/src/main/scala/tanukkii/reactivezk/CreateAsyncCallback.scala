package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import tanukkii.reactivezk.ZKOperations.{CreateFailure, Created}

trait CreateAsyncCallback {
  import KeeperExceptionConverter._

  def createAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ActorRef, String) => Unit = {
    (rc: Int, path: String, ctx: ActorRef, name: String) => rc.toKeeperExceptionOpt(path) match {
      case Some(e) => ctx ! CreateFailure(e)
      case None => ctx ! Created(path, name)
    }
  }
}
