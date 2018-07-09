import groovy.xml.*
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import hudson.*
import hudson.model.*
import jenkins.model.*

println "Inspecting all Maven Jobs for Maven Version"
// Delete the missed Maven Jobs
jobs = Jenkins.instance.getAllItems(hudson.maven.MavenModuleSet.class).each {  job ->
	println "About to Inspect Maven Job ${job.name}"
	def wrks = new File("/var/jenkins/workspace/${job.name}")
	if (wrks.exists()) {
		def pomFile = new File(wrks,'pom.xml')
		if (pomFile.exists()) {
			def pomXml = new XmlSlurper().parse(pomFile)
			def versionNode =  pomXml.depthFirst().find{ node -> node.name() == 'version'}
			println "Version node ${versionNode}"
			println "Version direkt: " + pomXml.version
			println "Whole pom.xml parsed"
			println XmlUtil.serialize(pomXml).toString()
		} else {
			println "No pom.xml found for ${job.name}, probably multi project build"
		}
	} else {
		println "${job.name} has not been built, cannot inspect pom.xml"
	}
}