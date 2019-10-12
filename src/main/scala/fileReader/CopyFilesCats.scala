package fileReader

import java.io._

import cats.effect.concurrent.Semaphore
import cats.effect.{ Concurrent, IO, Resource }
import cats.implicits._

class CopyFilesCats {

  def copy(from: File, to: File)(implicit concurrent: Concurrent[IO]): IO[Long] =
    for {
      guard <- Semaphore[IO](1)
      count <- inputOutputStream(from, to, guard).use {
                case (in, out) => guard.withPermit(transfer(in, out))
              }
    } yield count

  private def transmit(origin: InputStream, destination: OutputStream, buffer: Array[Byte], acc: Long): IO[Long] =
    for {
      amount <- IO(origin.read(buffer, 0, buffer.length))
      count <- if (amount > -1)
                IO(destination.write(buffer, 0, amount)) >> transmit(origin, destination, buffer, acc + amount)
              else IO.pure(acc)
    } yield count

  private def transfer(from: InputStream, to: OutputStream): IO[Long] =
    for {
      buffer <- IO(new Array[Byte](128))
      total  <- transmit(from, to, buffer, 0L)
    } yield total

  private def inputStream(f: File, guard: Semaphore[IO]): Resource[IO, FileInputStream] =
    Resource.make {
      IO(new FileInputStream(f))
    } { inputStream =>
      guard.withPermit {
        IO(inputStream.close()).handleErrorWith(_ => IO.unit)
      }
    }

  private def outputStream(f: File, guard: Semaphore[IO]): Resource[IO, FileOutputStream] =
    Resource.make {
      IO(new FileOutputStream(f))
    } { outputStream =>
      guard.withPermit {
        IO(outputStream.close()).handleErrorWith(_ => IO.unit)
      }
    }

  private def inputOutputStream(input: File,
                                output: File,
                                guard: Semaphore[IO]): Resource[IO, (FileInputStream, FileOutputStream)] =
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
