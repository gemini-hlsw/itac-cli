// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import cats.effect._
import cats.implicits._
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.Opts
import java.io.File
import edu.gemini.tac.qengine.p1.io.JointIdGen
import edu.gemini.tac.qengine.ctx.Partner
import edu.gemini.tac.qengine.util.Percent
import edu.gemini.tac.qengine.ctx.Site
import edu.gemini.spModel.core.Semester

object Main extends CommandIOApp(
  name = "itac",
  header = "ITAC Command Line Interface"
) {

  val partners = Map(
    "US" -> Partner("US", "United States", Percent(0.5), Set(Site.north, Site.south)),
    "CL" -> Partner("CL", "Chile", Percent(0.5), Set(Site.north, Site.south)),
    "AR" -> Partner("AR", "Argentina", Percent(0.5), Set(Site.north, Site.south)),
    "CA" -> Partner("CA", "Canada", Percent(0.5), Set(Site.north, Site.south)),
    "BR" -> Partner("BR", "Brazil", Percent(0.5), Set(Site.north, Site.south)),
  )

  val when = new Semester(2019, Semester.Half.B).getMidpointDate(edu.gemini.spModel.core.Site.GN).getTime()

  def main: Opts[IO[ExitCode]] =
    Opts.unit.map { _ =>

      IO(System.setProperty("edu.gemini.model.p1.schemaVersion", "2020A")) *>
      ProposalLoader[IO](partners, when)
        .loadMany(new File("/Users/rnorris/proposals-xml"))
        .runA(JointIdGen(1))
        .flatMap { ps =>
          ps.traverse_ {
            case (f, Left(errs)) => IO(println(s"--- $f")) *> errs.traverse(e => IO(println(e)))
            case (f, Right(ps))  => ps.traverse(p => IO(s"--- $f\n$p"))
          }
        }
        .as(ExitCode.Success)

    }

  }
