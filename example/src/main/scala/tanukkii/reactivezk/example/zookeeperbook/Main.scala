package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.ActorSystem

object Main extends App {

  val system = ActorSystem("zookeeperbook")

  system.actorOf(MasterSupervisor.props, "master-supervisor")
}
