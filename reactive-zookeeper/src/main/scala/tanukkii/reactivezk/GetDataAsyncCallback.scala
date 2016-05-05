package tanukkii.reactivezk

import akka.actor.{Actor, ActorRef}
import org.apache.zookeeper.data.Stat
import tanukkii.reactivezk.ZKOperations.{GetDataFailure, DataGot}

trait GetDataAsyncCallback {
  import KeeperExceptionConverter._

  def getDataAsyncCallback(implicit sender: ActorRef = Actor.noSender): (Int, String, ContextEnvelope, Array[Byte], Stat) => Unit = {
    (rc: Int, path: String, ctx: ContextEnvelope, data: Array[Byte], stat: Stat) => rc.toKeeperExceptionOpt(path) match {
      case None => ctx.sender ! DataGot(path, data, stat, ctx.originalCtx)
      case Some(e) => ctx.sender ! GetDataFailure(e, ctx.originalCtx)
    }
  }
}
