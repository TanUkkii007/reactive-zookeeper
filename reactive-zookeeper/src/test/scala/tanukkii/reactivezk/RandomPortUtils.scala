package tanukkii.reactivezk

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

/*
 * original source code
 * https://github.com/spray/spray/blob/76ab89c25ce6d4ff2c4b286efcc92ee02ced6eff/spray-util/src/main/scala/spray/util/Utils.scala#L35-L46
 */

object RandomPortUtils {
  def temporaryServerAddress(interface: String = "127.0.0.1"): InetSocketAddress = {
    val serverSocket = ServerSocketChannel.open()
    try {
      serverSocket.socket.bind(new InetSocketAddress(interface, 0))
      val port = serverSocket.socket.getLocalPort
      new InetSocketAddress(interface, port)
    } finally serverSocket.close()
  }

  def temporaryServerHostnameAndPort(interface: String = "127.0.0.1"): (String, Int) = {
    val socketAddress = temporaryServerAddress(interface)
    socketAddress.getHostName -> socketAddress.getPort
  }
}