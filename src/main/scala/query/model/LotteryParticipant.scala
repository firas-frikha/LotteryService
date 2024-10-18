package query.model

final case class LotteryParticipant(participantId: String,
                                    participantFirstName: String,
                                    participantLastName: String,
                                    lotteryId: String)
