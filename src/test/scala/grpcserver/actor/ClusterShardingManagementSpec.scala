package grpcserver.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.cluster.sharding.typed.scaladsl.Entity
import grpcserver.middleware.ClusterShardingManager
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Clock
import scala.util.{Failure, Success}

class ClusterShardingManagementSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with MockFactory {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val testKit = ActorTestKit()


  "ClusterShardingManagement" when {
    val clock = Clock.systemUTC()

    "Receiving RegisterEntity message" must {
      "Succeed to register entity when" when {
        "clusterShardingManagement succeed" in {

          val clusterShardingManagerMock = mock[ClusterShardingManager]
          (clusterShardingManagerMock.init(_: Entity[_, _]))
            .expects(*)
            .returns(Success(()))

          val clusterShardingManagementActor = testKit.spawn(ClusterShardingManagement(clusterShardingManagerMock, clock))

          val outputProbe = testKit.createTestProbe[ClusterShardingManagement.Output]()

          clusterShardingManagementActor.tell(
            ClusterShardingManagement.RegisterEntity(outputProbe.ref)
          )
          outputProbe.expectMessage(
            ClusterShardingManagement.SuccessfulEntityRegistration
          )
        }
      }

      "Fail to register entity when" when {
        "clusterShardingManagement fails" in {
          val exception = new RuntimeException("Unknown exception")

          val clusterShardingManagerMock = mock[ClusterShardingManager]
          (clusterShardingManagerMock.init(_: Entity[_, _]))
            .expects(*)
            .returns(Failure((exception)))

          val clusterShardingManagementActor = testKit.spawn(ClusterShardingManagement(clusterShardingManagerMock, clock))

          val outputProbe = testKit.createTestProbe[ClusterShardingManagement.Output]()

          clusterShardingManagementActor.tell(
            ClusterShardingManagement.RegisterEntity(outputProbe.ref)
          )
          outputProbe.expectMessage(
            ClusterShardingManagement.FailedEntityRegistration
          )
        }
      }
    }
  }
}
