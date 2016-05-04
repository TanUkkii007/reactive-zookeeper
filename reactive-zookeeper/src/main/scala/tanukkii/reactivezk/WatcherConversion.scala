package tanukkii.reactivezk

import org.apache.zookeeper.{Watcher, WatchedEvent}

trait WatcherConversion {
  implicit def toWatcher(f: WatchedEvent => Unit): Watcher = new Watcher {
    def process(event: WatchedEvent): Unit = f(event)
  }
}

object WatcherConversion extends WatcherConversion