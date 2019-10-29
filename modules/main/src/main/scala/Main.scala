// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.itac

import cats.effect._
import cats.implicits._
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.Opts
import com.monovore.decline.Command

object Main extends CommandIOApp(
  name = "itac",
  header = "ITAC Command Line Interface"
) {

  val init = Command(name = "init", header = "Initialize an ITAC workspace.") { Opts.unit }
  val ls   = Command(name = "ls", header = "List proposals in the current workspace.") { Opts.unit }

  def main: Opts[IO[ExitCode]] =
    (Opts.subcommand(init) |+| Opts.subcommand(ls)
    ).map(s => IO(println(s)).as(ExitCode.Success))

}
