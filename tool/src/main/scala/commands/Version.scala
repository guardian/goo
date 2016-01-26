package goo.version

import goo.Command

class VersionCommand() extends Command {

  private val version: String = try {
    // Find the version of this library. Taken from:
    // https://gualtierotesta.wordpress.com/2015/07/05/java-how-to-check-jar-version-at-runtime/
    val thisClass = classOf[VersionCommand];

    // Find the path of the compiled class
    val classPath: String = thisClass.getResource(thisClass.getSimpleName() + ".class").toString();

    // Find the path of the lib which includes the class
    val libPath: String = classPath.substring(0, classPath.lastIndexOf("!"));

    // Find the path of the file inside the lib jar
    val filePath: String = libPath + "!/META-INF/MANIFEST.MF";

    // We look at the manifest file, getting two attributes out of it
    val manifest = new java.util.jar.Manifest(new java.net.URL(filePath).openStream());
    val attr = manifest.getMainAttributes();
    attr.getValue("Specification-Version")
  } catch {
    case e: Exception => "Unable to find version"
  }

  override def executeImpl() {
    println(s"Goo version: ${version}")
  }
}