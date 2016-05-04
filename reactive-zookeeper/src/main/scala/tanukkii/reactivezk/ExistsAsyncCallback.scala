package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.data.Stat
import tanukkii.reactivezk.ZKOperations.{ExistsFailure, DoesExist}

trait ExistsAsyncCallback {
  import KeeperExceptionConverter._

  def existsAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ActorRef, Stat) => Unit = {
    (rc: Int, path: String, ctx: ActorRef, stat: Stat) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx ! DoesExist(path, stat)
      case Some(e) => ctx ! ExistsFailure(e)
    }
  }
}