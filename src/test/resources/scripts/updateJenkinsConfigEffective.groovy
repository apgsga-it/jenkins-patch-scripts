import groovy.xml.*
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
def boolean dry = true
def jobNameToProcess = "Java8mig com.affichage.geo.lib"
def cntUpdated = 0
def cntProcessed = 0 
Jenkins.instance.getAllItems(hudson.maven.MavenModuleSet.class).each {job ->
  println "Processing Job " + job.fullName + " start"
  cntProcessed++
  def configXMLFile = job.getConfigFile().getFile().getAbsolutePath();
  println configXMLFile
  def configXml = new XmlSlurper().parse(configXMLFile)
  println("Config xml:")
  println XmlUtil.serialize(configXml).toString()
  def jenkinsMvnGlobalConfig = configXml.globalSettings.find {
	it.@class.toString().equals("org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider")
  }
  if (jenkinsMvnGlobalConfig == null) {
	println "Config not found, returning"
	return
  }
  jenkinsMvnGlobalConfig.replaceNode { node ->
	globalSettings('class': "jenkins.mvn.DefaultGlobalSettingsProvider")
  }
  println("Updated Config xml")
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