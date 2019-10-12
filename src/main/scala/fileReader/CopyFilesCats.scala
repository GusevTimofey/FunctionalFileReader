package fileReader

import java.io._
import cats.effect.concurrent.Semaphore
import cats.effect.{ Concurrent, IO, Resource, Sync }
import cats.implicits._

class CopyFilesCats {

  def copy[F[_]: Concurrent](from: File, to: File, bufferSize: Int): F[Long] =
    for {
      guard <- Semaphore[F](1)
      count <- inputOutputStream(from, to, guard).use {
                case (in, out) => guard.withPermit(transfer(in, out, bufferSize))
              }
    } yield count

  private def transmit[F[_]: Sync](origin: InputStream,
                                   destination: OutputStream,
                                   buffer: Array[Byte],
                                   acc: Long): F[Long] =
    for {
      amount <- Sync[F].delay(origin.read(buffer, 0, buffer.length))
      count <- if (amount > -1)
                Sync[F].delay(destination.write(buffer, 0, amount)) >> transmit(origin,
                                                                                destination,
                                                                                buffer,
                                                                                acc + amount)
              else Sync[F].pure(acc)
    } yield count

  private def transfer[F[_]: Sync](from: InputStream, to: OutputStream, bufferSize: Int): F[Long] =
    for {
      buffer <- Sync[F].delay(new Array[Byte](bufferSize))
      total  <- transmit(from, to, buffer, 0L)
    } yield total

  private def inputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileInputStream] =
    Resource.make {
      Sync[F]
        .delay(new FileInputStream(f))
        .handleErrorWith(_ => Sync[F].raiseError(new IllegalAccessException("origin file cannot be open")))
    } { inputStream =>
      guard.withPermit {
        Sync[F].delay(inputStream.close()).handleErrorWith(_ => Sync[F].unit)
      }
    }

  private def outputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileOutputStream] =
    Resource.make {
      Sync[F]
        .delay(new FileOutputStream(f))
        .handleErrorWith(_ => Sync[F].raiseError(new IllegalAccessException("destination file cannot be open")))
    } { outputStream =>
      guard.withPermit {
        Sync[F].delay(outputStream.close()).handleErrorWith(_ => Sync[F].unit)
      }
    }

  private def inputOutputStream[F[_]: Sync](input: File,
                                            output: File,
                                            guard: Semaphore[F]): Resource[F, (FileInputStream, FileOutputStream)] =
    for {
      inStream  <- inputStream(input, guard)
      outStream <- outputStream(output, guard)
    } yield (inStream, outStream)

}
