package io.constellation.stream.delivery

/** Delivery guarantee level for a source connector. */
sealed trait DeliveryGuarantee

object DeliveryGuarantee {
  case object AtMostOnce  extends DeliveryGuarantee
  case object AtLeastOnce extends DeliveryGuarantee
}
