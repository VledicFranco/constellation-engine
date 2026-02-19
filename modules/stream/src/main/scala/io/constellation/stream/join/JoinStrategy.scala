package io.constellation.stream.join

/** Join strategies for merging multiple streams at a confluence point.
  *
  * These determine how elements from two input streams are paired when they arrive at different
  * rates.
  */
sealed trait JoinStrategy

object JoinStrategy {

  /** Pair the latest value from each side. When one side emits, pair with the most recent value
    * from the other. This is the default for Seq + scalar merges.
    */
  case object CombineLatest extends JoinStrategy

  /** Strict 1:1 pairing. Waits for both sides to emit before producing an output. The stream
    * terminates when either side ends.
    */
  case object Zip extends JoinStrategy

  /** Buffer one side until the other emits. Useful for rate-mismatched streams where no data should
    * be dropped.
    *
    * @param maxBuffer
    *   Maximum elements to buffer before applying backpressure
    */
  final case class Buffer(maxBuffer: Int = 1024) extends JoinStrategy
}
