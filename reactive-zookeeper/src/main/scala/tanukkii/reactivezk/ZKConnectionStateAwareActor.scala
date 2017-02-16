package tanukkii.reactivezk

import akka.actor.Actor

private [reactivezk] sealed trait ZooKeeperConnectionState
private [reactivezk] case object DisconnectedState extends ZooKeeperConnectionState
private [reactivezk] case object SyncConnectedState extends ZooKeeperConnectionState

trait ZKConnectionStateAwareActor extends Actor {
  var syncConnected = false

  def receive: Receive = {
    case SyncConnectedState =>
      syncConnected = true
    case DisconnectedState =>
      syncConnected = false
    case msg if syncConnected && receiveSyncConnected.isDefinedAt(msg) =>
      receiveSyncConnected(msg)
    case msg if !syncConnected && receiveDisconnected.isDefinedAt(msg) =>
      receiveDisconnected(msg)
  }

  def receiveSyncConnected: Receive

  def receiveDisconnected: Receive
}
