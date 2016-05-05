package tanukkii.reactivezk.example.zookeeperbook

case class ChildrenCache(children: List[String] = List.empty) {
  def diff(that: ChildrenCache) = ChildrenCache((children.toSet -- that.children.toSet).toList)

  def isEmpty: Boolean = children.isEmpty
}