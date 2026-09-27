package example

import example.i18n.Strings

object CompileFailure:
  def broken(strings: Strings): String =
    strings.common.doesNotExist
