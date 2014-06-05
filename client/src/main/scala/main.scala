package goo

object GooClient {

  // A bootstrapper that allows Goo users to run the latest maven-hosted frontend-goo-tool.
  def main(args: Array[String]) {
    GooCommand.run(args)
  }
}
