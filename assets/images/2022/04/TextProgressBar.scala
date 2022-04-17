//https://www.stevenskelton.ca/emoji-progress-bar-for-sass-integrations

class TextProgressBar(progressCharacters: Array[_]) {

  def bar(progress: Float, width: Int): String = {
    require(0 <= progress && progress <= 1 && width > 0)
    val completeWidth = math.floor(progress * width).toInt
    val partialWidth = (progress * width) % 1
    val progressIndex = math.floor(partialWidth * (progressCharacters.size - 1)).toInt
    val progressChar = if (width == completeWidth) "" else progressCharacters(progressIndex).toString
    val completeBar = progressCharacters.last.toString * completeWidth
    val remainingBar = progressCharacters.head.toString * (width - completeWidth - 1)
    s"$completeBar$progressChar$remainingBar"
  }

  def create(progress: Float, totalCharacters: Int): String = {
    val line = s"[${bar(progress, totalCharacters - 7)}] ${("  " + math.round(progress * 100)).takeRight(3)}%"
    println(line)
    line
  }
}

class TextProgressBarSpec extends org.specs2.mutable.Specification {

  sequential

  "integers" should {

    val textProgressBar = new TextProgressBar(Array(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100))

    "bar" in {
      "match 0%" in {
        textProgressBar.bar(0, 1) === "0"
      }
      "match 0.1%" in {
        textProgressBar.bar(0.001f, 1) === "0"
      }
      "match 5%" in {
        textProgressBar.bar(0.05f, 1) === "0"
      }
      "match 9.9%" in {
        textProgressBar.bar(0.099f, 1) === "0"
      }
      "match 10%" in {
        textProgressBar.bar(0.10f, 1) === "10"
      }
      "match 10.1%" in {
        textProgressBar.bar(0.101f, 1) === "10"
      }
      "match 49%" in {
        textProgressBar.bar(0.49f, 1) === "40"
      }
      "match 50%" in {
        textProgressBar.bar(0.51f, 1) === "50"
      }
      "handle 89%" in {
        textProgressBar.bar(0.89f, 1) === "80"
      }
      "match 90%" in {
        textProgressBar.bar(0.90f, 1) === "90"
      }
      "match 99%" in {
        textProgressBar.bar(0.99f, 1) === "90"
      }
      "match 100%" in {
        textProgressBar.bar(1, 1) === "100"
      }
    }
  }

  "multiple characters" should {

    val textProgressBar = new TextProgressBar(Array(":sloth:", ":working:", ":working:", ":working:", ":working:", ":working-on-it:", ":working-on-it:", ":working-on-it:", ":working-on-it:", ":firecracker:", ":done-slant:"))

    "bar" in {
      "match 0%" in {
        textProgressBar.bar(0, 1) === ":sloth:"
      }
      "match 5%" in {
        textProgressBar.bar(0.05f, 1) === ":sloth:"
      }
      "match 11%" in {
        textProgressBar.bar(0.11f, 1) === ":working:"
      }
      "match 49%" in {
        textProgressBar.bar(0.49f, 1) === ":working:"
      }
      "match 50%" in {
        textProgressBar.bar(0.51f, 1) === ":working-on-it:"
      }
      "handle 89%" in {
        textProgressBar.bar(0.89f, 1) === ":working-on-it:"
      }
      "match 90%" in {
        textProgressBar.bar(0.90f, 1) === ":firecracker:"
      }
      "match 99%" in {
        textProgressBar.bar(0.99f, 1) === ":firecracker:"
      }
      "match 100%" in {
        textProgressBar.bar(1, 1) === ":done-slant:"
      }
    }
  }

  "single characters" should {
    val textProgressBar = new TextProgressBar(Array(' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█'))

    "have correct length" in {
      "handle zero" in {
        textProgressBar.bar(0, 50).length === 50
        textProgressBar.create(0, 50).length === 50
      }
      "handle 50%" in {
        textProgressBar.bar(0.5f, 50).length === 50
        textProgressBar.create(0.5f, 50).length === 50
      }
      "handle 100%" in {
        textProgressBar.bar(1, 50).length === 50
        textProgressBar.create(1, 50).length === 50
      }
      "handle zero" in {
        textProgressBar.bar(0, 53).length === 53
        textProgressBar.create(0, 53).length === 53
      }
      "handle 50%" in {
        textProgressBar.bar(0.5f, 53).length === 53
        textProgressBar.create(0.5f, 53).length === 53
      }
      "handle 100%" in {
        textProgressBar.bar(1, 53).length === 53
        textProgressBar.create(1, 53).length === 53
      }
    }
    "bar" in {
      "match 0%" in {
        textProgressBar.bar(0, 1) === " "
      }
      "match 1%" in {
        textProgressBar.bar(0.01f, 1) === " "
      }
      "match 12.5%" in {
        textProgressBar.bar(0.13f, 1) === "▏"
      }
      "match 25%" in {
        textProgressBar.bar(0.26f, 1) === "▎"
      }
      "match 37.5%" in {
        textProgressBar.bar(0.38f, 1) === "▍"
      }
      "match 50%" in {
        textProgressBar.bar(0.51f, 1) === "▌"
      }
      "handle 62.5%" in {
        textProgressBar.bar(0.63f, 1) === "▋"
      }
      "match 75%" in {
        textProgressBar.bar(0.76f, 1) === "▊"
      }
      "match 87.5%" in {
        textProgressBar.bar(0.88f, 1) === "▉"
      }
      "match 99%" in {
        textProgressBar.bar(0.99f, 1) === "▉"
      }
      "match 100%" in {
        textProgressBar.bar(1, 1) === "█"
      }
    }
  }

}
