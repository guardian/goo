package goo

object GooDevBuild {

  // A copy of GooClient that allows Goo developers to run the local frontend-goo-tool.
  def main(args: Array[String]) {
    GooCommand.run(args)
  }
}
