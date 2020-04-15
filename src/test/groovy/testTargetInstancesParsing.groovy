import groovy.json.JsonSlurper

// JHE: Kind of stupid test, but, better than nothing ...

def pf = new patchfunctions()
def pd = new patchDeployment()

println "Starting getTargetInstance test ..."
assert pf.getTargetInstance("dev-stb", getTargetSystemMappingJson()) != null : "dev-stb should be define as Target!!"
assert pf.getTargetInstance("DEV-CHTI211", getTargetSystemMappingJson()) != null : "DEV-CHTI211 should be define as Target!!"
assert pf.getTargetInstance("DEV-CHPI211", getTargetSystemMappingJson()) != null : "DEV-CHPI211 should be define as Target!!"
assert pf.getTargetInstance("dev-ondemandwwww", getTargetSystemMappingJson()) == null : "dev-ondemandwwww is not define as a Target!!"
println "Testing of getTargetInstance done, OK"

def getTargetSystemMappingJson() {
	def configFileLocation = "src/test/resources/TargetSystemMappings.json"
	def targetSystemFile = new File(configFileLocation)
	assert targetSystemFile.exists()
	return new JsonSlurper().parseText(targetSystemFile.text)
}