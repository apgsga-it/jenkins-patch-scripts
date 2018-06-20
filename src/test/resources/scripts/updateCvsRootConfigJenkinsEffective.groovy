import groovy.xml.*
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
def boolean dry = true
// def jobNameToProcess = "Java8mig com.affichage.geo.lib"
def jobNameToProcess = null
def cntUpdated = 0
def cntProcessed = 0 
Jenkins.instance.getAllItems(hudson.maven.MavenModuleSet.class).each {job ->
  println "Processing Job " + job.fullName + " start"
  def configXMLFile = job.getConfigFile().getFile().getAbsolutePath();
  def configXml = new XmlSlurper().parse(configXMLFile)
  println "Before Update: "
  println XmlUtil.serialize(configXml).toString()
  println " "
  def cvsRootNode =  configXml.depthFirst().find{ node -> node.name() == 'cvsRoot'}
  if (cvsRootNode != null) {
	  cvsRootNode.replaceNode {
		  cvsRoot('${CVS_ROOT}')
	  }
  }
 println "After Update: "
 println XmlUtil.serialize(configXml).toString()
 Source xmlInput=new StreamSource(new StringReader(XmlUtil.serialize(configXml)));
  if (!dry) {
	  if (jobNameToProcess == null || jobNameToProcess.equals(job.fullName)) {
		  println "Updateing " + job.fullName
		  job.updateByXml(xmlInput)
		  cntUpdated++
		  println "Done"
	  }
  } else {
	  println "Skipping Update"
  }
  println "Processing Job " + job.fullName + " finished"
  
}
println "Jobs processed: ${cntProcessed} ,  Jobs updated: ${cntUpdated}"