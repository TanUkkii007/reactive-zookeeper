package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.ActorSystem
import tanukkii.reactivezk.{ ZKSessionSettings, ZKSessionSupervisorSettings, ZooKeeperSessionActor }
import tanukkii.reactivezk.example.zookeeperbook.ClientProtocol.SubmitTask

import scala.concurrent.duration._

object ClientMain extends App {

  val system = ActorSystem("zookeeperbook")

  import system.dispatcher

  val settings = ZKSessionSettings(system)

  val supervisorSettings = ZKSessionSupervisorSettings(Client.props, "client", isConnectionStateAware = false)

  val client  = system.actorOf(ZooKeeperSessionActor.props(settings, supervisorSettings), "zookeeper-session")

  system.scheduler.schedule(1 second, 1 second, client, SubmitTask(TaskObject("Sample task")))
}
