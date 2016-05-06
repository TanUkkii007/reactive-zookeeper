package tanukkii.reactivezk.example.zookeeperbook

import akka.actor.ActorSystem

object MasterMain extends App {

  val system = ActorSystem("zookeeperbook")

  system.actorOf(MasterSupervisor.props, "master-supervisor")
}
