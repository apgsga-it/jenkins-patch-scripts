import groovy.xml.*
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
Jenkins.instance.getAllItems(hudson.maven.MavenModuleSet.class).each {job ->
  println "Processing Job " + job.fullName + " start"
  def configXMLFile = job.getConfigFile().getFile().getAbsolutePath();
  println configXMLFile
  def configXml = new XmlSlurper().parse(configXMLFile)
  println("Config xml:")
  println XmlUtil.serialize(configXml).toString()
  
}
