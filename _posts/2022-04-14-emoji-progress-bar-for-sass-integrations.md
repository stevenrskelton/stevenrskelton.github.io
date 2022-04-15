---
title: "Emoji Progress Bar for SaaS Integrations"
categories:
  - Platform
  - Scala
tags:
  - Slack
---
The command line progress bar was the first step towards graphical UI.  It was an exciting addition to a numerical percent ticking away as a running task took forever to complete. It started with safe for everywhere ascii characters 
```
[======>                   ]  20.3%
```
Later evolving into fancifal Unicode updating before your eyes:

![https://mike42.me/blog/2018-06-make-better-cli-progress-bars-with-unicode-block-characters](/assets/images/2022/04/2018-06-progress-bar-animated.gif)

Today shimmering graphical progress bars are the norm, designed to compel your full attention until completion. If your business isn't about dopamine drips and eyeballs the graphics might be less important and arguably a worse implementation than the older simplicity. A large use-case for a simple approach is in SaaS integrations. Workflows across multiple SaaS providers quickly run into issues of compatibility and portability. Simple ASCII implementations are user friendly, pleasant, and will always work.

My use-case is for a Slack bot to post status updates within Slack messages.  The provided Block Kit has a lot of beautify components to choose from but is missing a progress bar. I found an [updating progress bar](https://github.com/bcicen/slack-progress) build using the Slacker Python library (surprise it uses Unicode!) but I'm looking for something in Java or Scala.

A well-written blog [Make better CLI progress bars with Unicode block characters](https://mike42.me/blog/2018-06-make-better-cli-progress-bars-with-unicode-block-characters) is another Unicode progress bar in Python, but it inspired me to port the code to Scala.

It used a neat little set of block characters from Unicode:
```scala
Array(' ','▏','▎','▍','▌','▋','▊','▉','█')
```
In Scala, we simple implementation class would be:
```scala
class TextProgressBar(progressCharacters: Array[_]) {
  def bar(progress: Float, width: Int): String
}
```
Using `Array[_]` here allows flexibility from not just Unicode characters, but whatever is best supported on the platform. Instead of single Unicode representations, we might opt to native elements such as HTML or JSON to represent our progress blocks.

The array looks surprising like a linear progression, and it is! Consider a progress bar of length 1. If there are 10 items in the array, progress of 40% would return the 4<sup>th</sup> index. An overkill way to implement a `floor` would be:
```scala
TextProgressBar(Array(
  0, 10, 20, 30, 40, 
  50, 60, 70, 80, 90, 100
)).bar(progress: Float, 1)
```
This could be useful if instead of numbers, we wanted `< 10%`, `< 50%`, `< 90%`, and `100%` segments.  This implementation would easily separate jobs which have just started, are less than <sup>1</sup>/<sub>2</sub>, more than <sup>1</sup>/<sub>2</sub>, near complete, and done. In Slack we might want to use emojis:
![:sloth:](/assets/images/2022/04/slack_sloth.png), ![:working:](/assets/images/2022/04/slack_working.png), ![:working-on-it:](/assets/images/2022/04/slack_working-on-it.png), ![:firecracker:](/assets/images/2022/04/slack_firecracker.png), ![:done-slant:](/assets/images/2022/04/slack_done-slant.png)
```
TextProgressBar(Array(
  ":sloth:", ":working:", ":working:", ":working:", ":working:", 
  ":working-on-it:", ":working-on-it:", ":working-on-it:", ":working-on-it:", ":firecracker:", ":done-slant:"
)).bar(progress: Float, 1)
```


The Scala code for `bar` follows from the [mike42 blog](https://mike42.me/blog/2018-06-make-better-cli-progress-bars-with-unicode-block-characters) Python:
```scala
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
```
And now we have progress bar to use everywhere and can scale in width from from 1 to N characters.
<img src="/assets/images/2022/04/slack_done-slant.png" alt=":done-slant:" style="float:left">
```
Congratulations!
[█████████████████████████] 100%
```

{%
  include downloadsources.html
  src="/assets/images/2022/04/TextProgressBar.scala"
%}
