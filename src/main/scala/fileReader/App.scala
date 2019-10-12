package fileReader

import java.io.File

import cats.effect.{ ExitCode, IO, IOApp }

object App extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      _             <- IO(println(s"Enter origin file"))
      origin        <- IO(scala.io.StdIn.readLine())
      _             <- IO(println(s"Enter destination file"))
      destination   <- IO(scala.io.StdIn.readLine())
      copyFileClass <- IO.pure(new CopyFilesCats)
      orig          = new File(origin)
      dest          = new File(destination)
      count         <- copyFileClass.copy(orig, dest)
      _             <- IO(println(s"$count bytes copied from ${orig.getPath} to ${dest.getPath}"))
    } yield ExitCode.Success

}
