package ca.stevenskelton.examples.realtimeziohubgrpc.externaldata

import ca.stevenskelton.examples.realtimeziohubgrpc.DataRecord
import zio.{Chunk, Hub, Queue, Ref, UIO, ZIO, ZLayer, ULayer}

import scala.collection.immutable.HashSet

object ExternalDataLayer:
  val live: ULayer[ExternalDataLayer] = ZLayer.succeed(new ExternalDataLayer())

class ExternalDataLayer:

  def createService(
              dataJournal: Hub[DataRecord],
              databaseRef: Ref[Map[Int, DataRecord]],
              globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
            ): UIO[ExternalDataService] =
    for
      fetchQueue <- Queue.unbounded[Int]
    yield
      ExternalDataService(fetchQueue, dataJournal, databaseRef, globalSubscribersRef)
  
  
  
  

//object ExternalDataService:
//  
//  def layer(
//              dataJournal: Hub[DataRecord],
//              databaseRef: Ref[Map[Int, DataRecord]],
//              globalSubscribersRef: Ref[Set[Ref[HashSet[Int]]]],
//            ): ULayer[ExternalDataService] = 
//    ZLayer.fromZIO:
//      Queue.unbounded[Int].map:
//        fetchQueue => ExternalDataService(fetchQueue, dataJournal, databaseRef, globalSubscribersRef)



  
  

