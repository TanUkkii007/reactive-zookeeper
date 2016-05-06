package tanukkii.reactivezk

import org.apache.zookeeper.WatchedEvent

case class ZooKeeperWatchEvent(e: WatchedEvent)