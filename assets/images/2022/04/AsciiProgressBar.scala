object AsciiProgressBar {
  val progressCharacters = Array(' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█')

  def bar(progress: Float, width: Int): String = {
    require(0 <= progress && progress <= 1)
    val wholeWidth = math.floor(progress * width).toInt
    val remainderWidth = (progress * width) % 1
    val partWidth = math.floor(remainderWidth * (progressCharacters.size - 1)).toInt
    val partChar = if (width - wholeWidth == 0) "" else progressCharacters(partWidth).toString
    s"${progressCharacters.last.toString * wholeWidth}$partChar${progressCharacters.head.toString * (width - wholeWidth - 1)}"
  }

  def create(progress: Float, totalCharacters: Int): String = {
    val line = s"[${bar(progress, totalCharacters - 7)}] ${("  " + math.round(progress * 100)).takeRight(3)}%"
    println(line)
    line
  }
}

class AsciiProgressBarSpec extends org.specs2.mutable.Specification {

  sequential

  "length" should {
    "handle zero" in {
      AsciiProgressBar.bar(0, 50).length === 50
      AsciiProgressBar.create(0, 50).length === 50
    }
    "handle 50%" in {
      AsciiProgressBar.bar(0.5f, 50).length === 50
      AsciiProgressBar.create(0.5f, 50).length === 50
    }
    "handle 100%" in {
      AsciiProgressBar.bar(1, 50).length === 50
      AsciiProgressBar.create(1, 50).length === 50
    }
    "handle zero" in {
      AsciiProgressBar.bar(0, 53).length === 53
      AsciiProgressBar.create(0, 53).length === 53
    }
    "handle 50%" in {
      AsciiProgressBar.bar(0.5f, 53).length === 53
      AsciiProgressBar.create(0.5f, 53).length === 53
    }
    "handle 100%" in {
      AsciiProgressBar.bar(1, 53).length === 53
      AsciiProgressBar.create(1, 53).length === 53
    }
  }
  "bar" in {
    "match 0%" in {
      AsciiProgressBar.bar(0, 1) === " "
    }
    "match 12.5%" in {
      AsciiProgressBar.bar(0.13f, 1) === "▏"
    }
    "match 25%" in {
      AsciiProgressBar.bar(0.26f, 1) === "▎"
    }
    "match 37.5%" in {
      AsciiProgressBar.bar(0.38f, 1) === "▍"
    }
    "match 50%" in {
      AsciiProgressBar.bar(0.51f, 1) === "▌"
    }
    "handle 62.5%" in {
      AsciiProgressBar.bar(0.63f, 1) === "▋"
    }
    "match 75%" in {
      AsciiProgressBar.bar(0.76f, 1) === "▊"
    }
    "match 87.5%" in {
      AsciiProgressBar.bar(0.88f, 1) === "▉"
    }
    "match 100%" in {
      AsciiProgressBar.bar(1, 1) === "█"
    }
  }
}
