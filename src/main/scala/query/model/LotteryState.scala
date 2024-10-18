package query.model

object LotteryState extends Enumeration {
  type State = Value

  final val Closed = Value("CLOSED")
  final val Open = Value("OPEN")
}
