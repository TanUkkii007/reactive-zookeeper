package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.data.Stat
import tanukkii.reactivezk.ZKOperations.{GetDataFailure, DataGot}

trait GetDataAsyncCallback {
  import KeeperExceptionConverter._

  def getDataAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ActorRef, Array[Byte], Stat) => Unit = {
    (rc: Int, path: String, ctx: ActorRef, data: Array[Byte], stat: Stat) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx ! DataGot(path, data, stat)
      case Some(e) => ctx ! GetDataFailure(e)
    }
  }
}
