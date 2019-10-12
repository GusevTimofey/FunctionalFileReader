package fileReader

import java.io.File
import cats.effect.{ ExitCode, IO, IOApp }

object App extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      _           <- IO(println(s"Enter origin file"))
      origin      <- IO(scala.io.StdIn.readLine())
      _           <- IO(println(s"Enter destination file"))
      destination <- IO(scala.io.StdIn.readLine())
      _ <- if (origin == destination) IO.raiseError(new IllegalArgumentException("File's names mast be different"))
          else IO.unit
      copyFileClass <- IO.pure(new CopyFilesCats)
      orig          = new File(origin)
      dest          = new File(destination)
      _ <- if (dest.exists()) for {
            _ <- IO(println(s"Confirm that file ${dest.getName} will be overridden"))
            _ <- IO(scala.io.StdIn.readLine()).flatMap {
                  case "ok" => IO.unit
                  case _    => IO.raiseError(new IllegalAccessError("File cannot be overridden"))
                }
          } yield IO.unit
          else IO.unit
      count <- copyFileClass.copy[IO](orig, dest, 128)
      _     <- IO(println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}"))
    } yield ExitCode.Success

}
