package ca.stevenskelton.examples.grpcfiletransferzio

import ca.stevenskelton.examples.grpcfiletransferzio.file_service.{GetImageRequest, ImageChunk, SetImageResponse}
import ca.stevenskelton.examples.grpcfiletransferzio.file_service.ZioFileService.FileService
import io.grpc.StatusException
import zio.{IO, stream}

class FileServiceImpl extends FileService {

  override def getImage(request: GetImageRequest): stream.Stream[StatusException, ImageChunk] = {
    ???
  }

  override def setImage(request: stream.Stream[StatusException, ImageChunk]): IO[StatusException, SetImageResponse] = {
    ???
  }
}
