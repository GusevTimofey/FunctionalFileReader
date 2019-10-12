package fileReader

import java.io._
import cats.effect.concurrent.Semaphore
import cats.effect.{ Concurrent, IO, Resource, Sync }
import cats.implicits._

class CopyFilesCats {

  def copy[F[_]: Concurrent](from: File, to: File): F[Long] =
    for {
      guard <- Semaphore[F](1)
      count <- inputOutputStream(from, to, guard).use {
                case (in, out) => guard.withPermit(transfer(in, out))
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

  private def transfer[F[_]: Sync](from: InputStream, to: OutputStream): F[Long] =
    for {
      buffer <- Sync[F].delay(new Array[Byte](128))
      total  <- transmit(from, to, buffer, 0L)
    } yield total

  private def inputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileInputStream] =
    Resource.make {
      Sync[F].delay(new FileInputStream(f))
    } { inputStream =>
      guard.withPermit {
        Sync[F].delay(inputStream.close()).handleErrorWith(_ => Sync[F].unit)
      }
    }

  private def outputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileOutputStream] =
    Resource.make {
      Sync[F].delay(new FileOutputStream(f))
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

  @deprecated private def inputStreamFromResources(f: File): Resource[IO, FileInputStream] =
    Resource.fromAutoCloseable(IO(new FileInputStream(f)))

  @deprecated private def outputStreamFromResources(f: File): Resource[IO, FileOutputStream] =
    Resource.fromAutoCloseable(IO(new FileOutputStream(f)))

  @deprecated def inputOutputStreamWithResources(input: File,
                                                 output: File): Resource[IO, (FileInputStream, FileOutputStream)] =
    for {
      inStream  <- inputStreamFromResources(input)
      outStream <- outputStreamFromResources(output)
    } yield (inStream, outStream)
}
