package ca.stevenskelton.examples.realtimeziohubgrpc

import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.SyncRequest.Subscribe.DataSnapshots
import ca.stevenskelton.examples.realtimeziohubgrpc.sync_service.{Data, SyncRequest}
import zio.stream.ZStream
import zio.test.assertTrue
import zio.test.junit.JUnitRunnableSpec

class ZSyncServiceImplSpec extends JUnitRunnableSpec {

  // Client 1:
  //  - watch 1
  //  - update 1,2,3,4 (Time 1000)
  //  - update 1,2,3,4 (Time 2000)
  //  - update 1,2,3,4 (Time 3000)
  // Client 2:
  //  - watch 1,2
  // Client 3:
  //  - watch 1,3
  val SubscribeActions = Seq(
    (1, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 0)))))),
    (2, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 0), DataSnapshots.of(2, 0)))))),
    (3, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 0), DataSnapshots.of(3, 0)))))),
  )

  override def spec = suite("multiple client listeners")(
    test("All updated") {
      val ExpectedResponseCount = 15
      val streamActions = SubscribeActions ++ Seq(
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(1))))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(2))))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(3))))),
      )
      for
        userResponses <- BidirectionalTestClients(ZStream.fromIterable(streamActions), ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)

        //Client 1
        val client1Responses = userResponses.withFilter(_._1 == 1).map(_._2)
        client1Responses.foreach(o => println(o.toString))
        assertTrue(client1Responses.size == 3)

        //Client 2
        val client2Responses = userResponses.withFilter(_._1 == 2).map(_._2)
        client2Responses.foreach(o => println(o.toString))
        assertTrue(client2Responses.size == 6)

        //Client 3
        val client3Responses = userResponses.withFilter(_._1 == 3).map(_._2)
        client3Responses.foreach(o => println(o.toString))
        assertTrue(client3Responses.size == 6)
    },
    test("Unsubscribe by Id") {
      val ExpectedResponseCount = 13
      val streamActions = SubscribeActions ++ Seq(
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(1))))),
        (2, SyncRequest.of(SyncRequest.Action.Unsubscribe(SyncRequest.Unsubscribe.of(ids = Seq(1))))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(2))))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(3))))),
      )
      for
        userResponses <- BidirectionalTestClients(ZStream.fromIterable(streamActions), ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)
        assertTrue(userResponses.count(_._1 == 1) == 3) //Client 1
        assertTrue(userResponses.count(_._1 == 2) == 4) //Client 2
        assertTrue(userResponses.count(_._1 == 3) == 6) //Client 3
    },
    test("Unsubscribe All") {
      val ExpectedResponseCount = 12
      val streamActions = SubscribeActions ++ Seq(
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(1))))),
        (2, SyncRequest.of(SyncRequest.Action.Unsubscribe(SyncRequest.Unsubscribe.of(ids = Nil)))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(2))))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(3))))),
      )
      for
        userResponses <- BidirectionalTestClients(ZStream.fromIterable(streamActions), ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)
        assertTrue(userResponses.count(_._1 == 1) == 3) //Client 1
        assertTrue(userResponses.count(_._1 == 2) == 1) //Client 2
        assertTrue(userResponses.count(_._1 == 3) == 6) //Client 3
    },
    test("Limit Subscribe Response to before lastUpdate") {
      val ExpectedResponseCount = 2
      val streamActions = Seq(
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(1))))),
        (1, SyncRequest.of(SyncRequest.Action.Subscribe(SyncRequest.Subscribe.of(Seq(DataSnapshots.of(1, 2000000000L), DataSnapshots.of(2, 0)))))),
        (1, SyncRequest.of(SyncRequest.Action.Update(SyncRequest.Update.of(data = ZSyncServiceImplSpec.createData(2))))),
      )
      for
        userResponses <- BidirectionalTestClients(ZStream.fromIterable(streamActions), ExpectedResponseCount).responses
      yield
        assertTrue(userResponses.length == ExpectedResponseCount)

        assertTrue(userResponses.count(_._1 == 1) == 2) //Client 1
    }
  ) @@ zio.test.TestAspect.sequential
}

object ZSyncServiceImplSpec:
  def createData(batch: Int): Seq[Data] = Seq(
    Data.of(id = 1, field1 = s"id1-batch$batch"),
    Data.of(id = 2, field1 = s"id2-batch$batch"),
    Data.of(id = 3, field1 = s"id3-batch$batch"),
    Data.of(id = 4, field1 = s"id4-batch$batch"),
    Data.of(id = 5, field1 = s"id5-batch$batch"),
  )
