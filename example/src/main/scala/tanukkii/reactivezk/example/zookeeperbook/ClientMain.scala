package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.ActorSystem
import tanukkii.reactivezk.ReactiveZK
import tanukkii.reactivezk.example.zookeeperbook.ClientProtocol.SubmitTask
import scala.concurrent.duration._

object ClientMain extends App {

  val system = ActorSystem("zookeeperbook")

  import system.dispatcher

  val zookeeperSession = ReactiveZK(system).zookeeperSession

  val client = system.actorOf(Client.props(zookeeperSession))

  system.scheduler.schedule(1 second, 1 second, client, SubmitTask(TaskObject("Sample task")))
}
