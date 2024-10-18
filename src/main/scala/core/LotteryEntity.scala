package core

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}

import java.time.{Clock, LocalDate}
import scala.util.Random

object LotteryEntity {
  type Id = String
  type BallotId = String
  type CreatedAt = LocalDate

  //Define the Tag:
  final val Single = "lotteryEntity"
  final val Tags = Vector("tag-1", "tag-2", "tag-3")

  // Define the entity Type key:
  val entityTypeKey: EntityTypeKey[Command] = EntityTypeKey[Command](LotteryEntity.getClass.getSimpleName)


  // persistence actor initialization:
  def apply(id: Id,
            persistenceId: PersistenceId,
            clock: Clock): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = persistenceId,
      emptyState = EmptyState(id = id),
      commandHandler = (state, command) => state.applyCommand(command, clock),
      eventHandler = (state, event) => state.applyEvent(event)
    ).withTagger(event => Set(Tags(math.abs(event.id.hashCode % Tags.size))))


  // Commands that will be handled by the lottery entity
  sealed trait Command

  case class CreateCommand(lotteryName: String)
                          (val replyTo: ActorRef[CreateResult]) extends Command

  case class ParticipateCommand(ballotId: BallotId)
                               (val replyTo: ActorRef[ParticipateResult]) extends Command

  case class CloseLotteryCommand()
                                (val replyTo: ActorRef[CloseResult]) extends Command

  case class FetchLotteryWinner()
                               (val replyTo: ActorRef[FetchLotteryWinnerResult]) extends Command

  // Events that will be saved by the lottery entity
  sealed trait Event {

    def id: Id
  }


  final case class CreatedLotteryEvent(override val id: Id,
                                       lotteryName: String,
                                       createdAt: CreatedAt) extends Event

  final case class BallotAddedEvent(override val id: Id,
                                    newBallotsList: Set[BallotId]) extends Event

  final case class ClosedLotteryEvent(override val id: Id,
                                      lotteryName: String,
                                      createdAt: CreatedAt,
                                      ballotsList: Set[BallotId],
                                      winner: Option[BallotId]) extends Event

  // Results of the executed commands
  sealed trait Result

  // Results of Create command
  sealed trait CreateResult extends Result

  final case class SuccessfulCreateResult(id: Id) extends CreateResult

  final case class UnsupportedCreateResult(message: String) extends CreateResult

  // Results of Participate command:

  sealed trait ParticipateResult extends Result

  final case class SuccessfulParticipateResult(ballotId: BallotId) extends ParticipateResult

  final case class UnsupportedParticipateResult(message: String) extends ParticipateResult

  // Results of Close command

  sealed trait CloseResult extends Result

  final case class SuccessfulCloseResult(message: String) extends CloseResult

  final case class UnsupportedCloseResult(message: String) extends CloseResult

  // Results of Fetch winner command

  sealed trait FetchLotteryWinnerResult extends Result

  final case class SuccessfulFetchLotteryWinnerResult(winner: Option[BallotId]) extends FetchLotteryWinnerResult

  final case class UnsupportedFetchLotteryWinnerResult(message: String) extends FetchLotteryWinnerResult

  sealed trait State {
    def id: Id

    def applyCommand(command: Command,
                     clock: Clock): ReplyEffect[Event, State]

    def applyEvent(event: Event): State
  }

  case class EmptyState(override val id: Id) extends State {
    override def applyCommand(command: Command,
                              clock: Clock): ReplyEffect[Event, State] =
      command match {
        case createCommand: core.LotteryEntity.CreateCommand =>
          Effect.persist(
            CreatedLotteryEvent(
              id = id,
              lotteryName = createCommand.lotteryName,
              createdAt = LocalDate.now(clock)
            )
          ).thenReply(createCommand.replyTo)(newState => SuccessfulCreateResult(newState.id))
        case actualCommand: ParticipateCommand =>
          Effect.reply(actualCommand.replyTo)(UnsupportedParticipateResult(s"Cannot Process ${actualCommand.getClass.getSimpleName} command, because lottery with id: $id do not exist!"))
        case actualCommand: CloseLotteryCommand =>
          Effect.reply(actualCommand.replyTo)(UnsupportedCloseResult(s"Cannot Process ${actualCommand.getClass.getSimpleName} command, because lottery with id: $id do not exist!"))
        case actualCommand: FetchLotteryWinner =>
          Effect.reply(actualCommand.replyTo)(UnsupportedFetchLotteryWinnerResult(s"Cannot Process ${actualCommand.getClass.getSimpleName} command, because lottery with id: $id do not exist!"))

      }

    override def applyEvent(event: Event): State =
      event match {
        case createdEvent: CreatedLotteryEvent =>
          ActiveState(
            id = id,
            createdAt = createdEvent.createdAt,
            lotteryName = createdEvent.lotteryName,
            registeredBallots = Set.empty)
        case _ =>
          throw new IllegalStateException(s"Unexpected ${event.getClass.getSimpleName} event in '${getClass.getSimpleName} state for entity '$id")
      }
  }


  case class ActiveState(override val id: Id,
                         createdAt: CreatedAt,
                         lotteryName: String,
                         registeredBallots: Set[BallotId]) extends State {
    override def applyCommand(command: Command,
                              clock: Clock): ReplyEffect[Event, State] =
      command match {
        case actualCommand: CreateCommand =>
          Effect.reply(actualCommand.replyTo)(UnsupportedCreateResult(s"Cannot Process ${actualCommand.getClass.getSimpleName} command, because lottery with '$id' is already created!"))
        case actualCommand: ParticipateCommand =>
          Effect.persist(BallotAddedEvent(
            id = id,
            newBallotsList = registeredBallots + actualCommand.ballotId)
          ).thenReply(actualCommand.replyTo)(_ => SuccessfulParticipateResult(actualCommand.ballotId))
        case actualCommand: CloseLotteryCommand =>
          val winner =
            if (registeredBallots.nonEmpty)
              Some(registeredBallots.toVector(Random.nextInt(registeredBallots.size)))
            else
              None
          Effect.persist(
            ClosedLotteryEvent(
              id = id,
              lotteryName = lotteryName,
              createdAt = createdAt,
              ballotsList = registeredBallots,
              winner = winner
            )
          ).thenReply(actualCommand.replyTo)(_ => SuccessfulCloseResult(s"Lottery with id $id is closed, the winner is $winner"))
        case actualCommand: FetchLotteryWinner =>
          Effect.reply(actualCommand.replyTo)(UnsupportedFetchLotteryWinnerResult(s"Cannot Process ${actualCommand.getClass.getSimpleName} command, because lottery with id: $id is not closed yet!"))
      }

    override def applyEvent(event: Event): State =
      event match {
        case actualEvent: BallotAddedEvent =>
          copy(
            registeredBallots = actualEvent.newBallotsList
          )

        case actualEvent: ClosedLotteryEvent =>
          ClosedState(
            id = id,
            lotteryName = lotteryName,
            createdAt = createdAt,
            finalBallots = registeredBallots,
            winner = actualEvent.winner)

        case actualEvent: CreatedLotteryEvent => throw new IllegalStateException(s"Unexpected ${actualEvent.getClass.getSimpleName} event in ${getClass.getSimpleName} state for entity '$id'!")
      }
  }

  case class ClosedState(override val id: Id,
                         lotteryName: String,
                         createdAt: CreatedAt,
                         finalBallots: Set[BallotId],
                         winner: Option[BallotId]) extends State {
    override def applyCommand(command: Command,
                              clock: Clock): ReplyEffect[Event, State] =
      command match {
        case actualCommand: FetchLotteryWinner =>
          Effect.reply(actualCommand.replyTo)(SuccessfulFetchLotteryWinnerResult(winner))
        case actualCommand: CreateCommand =>
          Effect.reply(actualCommand.replyTo)(UnsupportedCreateResult(s"Cannot process ${actualCommand.getClass.getSimpleName} command, because lottery with id: '$id' is closed!"))
        case actualCommand: ParticipateCommand =>
          Effect.reply(actualCommand.replyTo)(UnsupportedParticipateResult(s"Cannot process ${actualCommand.getClass.getSimpleName} command, because lottery with id: '$id' is closed!"))
        case actualCommand: CloseLotteryCommand =>
          Effect.reply(actualCommand.replyTo)(UnsupportedCloseResult(s"Cannot process ${actualCommand.getClass.getSimpleName} command, because lottery with id: '$id' is already closed!"))
      }

    override def applyEvent(event: Event): State =
      event match {
        case _ => throw new IllegalArgumentException(s"Unexpected event '${event.getClass.getSimpleName}' in ${getClass.getSimpleName} state for entity '$id'")
      }
  }
}
