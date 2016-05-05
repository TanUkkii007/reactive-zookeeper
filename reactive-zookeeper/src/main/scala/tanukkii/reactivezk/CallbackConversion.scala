package tanukkii.reactivezk

trait CallbackConversion extends StringCallbackConversion
  with DataCallbackConversion
  with StatCallbackConversion
  with ChildrenCallbackConversion
  with VoidCallbackConversion
  with WatcherConversion

object CallbackConversion extends CallbackConversion