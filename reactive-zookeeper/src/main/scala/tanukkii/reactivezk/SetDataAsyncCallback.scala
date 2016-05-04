package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.data.Stat
import tanukkii.reactivezk.ZKOperations.{SetDataFailure, DataSet}

trait SetDataAsyncCallback {
  import KeeperExceptionConverter._

  def setDataAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ActorRef, Stat) => Unit = {
    (rc: Int, path: String, ctx: ActorRef, stat: Stat) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx ! DataSet(path, stat)
      case Some(e) => ctx ! SetDataFailure(e)
    }
  }
}
