package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.ActorSystem

object WorkerMain extends App {

  val system = ActorSystem("zookeeperbook")

  system.actorOf(WorkerSupervisor.props, "worker-supervisor")
}
