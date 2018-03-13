import groovy.xml.*
import javax.xml.transform.Result
import javax.xml.transform.Source
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

def configFile = "src/test/resources/xml/config.xml"
println "Config File: ${configFile}"
def configXml = new XmlSlurper().parse(configFile)
println("Config xml:")
println XmlUtil.serialize(configXml).toString()
// Change node.
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
println("NEW Config xml")
println XmlUtil.serialize(configXml).toString()
Source xmlInput=new StreamSource(new StringReader(XmlUtil.serialize(configXml)));
TransformerFactory transformerFactory = TransformerFactory.newInstance();
Transformer transformer;
transformer = transformerFactory.newTransformer();
FileWriter writer = new FileWriter(new File("src/test/resources/xml/newconfig.xml"));
Result result = new StreamResult(writer);
transformer.transform(xmlInput, result);

