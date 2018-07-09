import hudson.*
println "Inspecting all Maven Jobs for Maven Version"
// Delete the missed Maven Jobs
jobs = Jenkins.instance.getAllItems(hudson.maven.MavenModuleSet.class).each {  job ->
	println "About to Inspect Maven Job ${job.name}"
	def configXMLFile = job.getConfigFile().getFile().getAbsolutePath();
	println configXMLFile
	def configXml = new XmlSlurper().parse(configXMLFile)
	println("Config xml:")
	println XmlUtil.serialize(configXml).toString()
}