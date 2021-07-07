# simple-pycharm-plugin

![Build](https://github.com/skuzi/simple-pycharm-plugin/workflows/Build/badge.svg)

<!-- Plugin description -->
This plugin contains inspections for not annotated variable declaration (such as in assignments or in function parameter list) and fixes that annotate such variables (for now it offers to add `str` annotation to single variable declaration and `int` annotation to all function parameters without annotations)

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be extracted by the [Gradle](/build.gradle.kts) during the build process.

<!-- Plugin description end -->

## Installation
  
- For debug purposes:
  1. Clone this repository to device
  2. Move to folder where repository was cloned to
  3. Run `./gradlew runIde`
  4. If plugin was launched first time, wait for resources to download and then for PyCharm to launch
  5. Now, if caret is placed at some not annotated variable declaration and `Alt+Enter` is pressed, warning window should open and offer to annotate such variable (or all variables in function parameter list)
  
  
- Requirements:
  - Java 8
  - Python 3.8

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
