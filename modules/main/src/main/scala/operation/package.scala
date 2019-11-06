package itac

import java.nio.file.Path
import java.nio.file.Paths

package object operation {

  implicit def string2path(s: String): Path =
    Paths.get(s)

}
