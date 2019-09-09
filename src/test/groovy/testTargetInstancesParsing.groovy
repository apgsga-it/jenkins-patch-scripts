import groovy.json.JsonSlurper

// JHE: Kind of stupid test, better would naturally be to call function within patchfunctions.groovy.
//	    But for this project, we're not really ready to run function with test configuration.
//		Therefore, for now, the below was just to test the syntax "offline" (without running any pipeline)

println "Starting isLightInstallation test ..."
assert isLightInstallation("dev-bsp",getTargetSystemMappingJson()) : "dev-bsp is a Light !!"
assert isLightInstallation("dev-ondemand",getTargetSystemMappingJson()) : "dev-ondemand is a Light !!"
assert !isLightInstallation("CHEI212",getTargetSystemMappingJson()) : "CHEI212 is not a Light !!"
assert !isLightInstallation("CHTI214",getTargetSystemMappingJson()) : "CHTI214 is not a Light !!"
assert !isLightInstallation("CHQI211",getTargetSystemMappingJson()) : "CHQI211 is not a Light !!"
assert !isLightInstallation("CHEI211",getTargetSystemMappingJson()) : "CHEI211 is not a Light !!"
println "Testing from isLightInstallation done, OK"

assert getTargetInstance("dev-ondemand", getTargetSystemMappingJson()) != null : "dev-ondemand should be define as Target!!"
assert getTargetInstance("dev-ondemandwwww", getTargetSystemMappingJson()) == null : "dev-ondemandwwww is not define as a Target!!"

def isLightInstallation(target,targetSystemMappingJson) {
	def isLight = false
	targetSystemMappingJson.targetInstances.each ({ targetInstance ->
		if(targetInstance.name == target) {
			targetInstance.services.each ({ service ->
				isLight = service.name == "it21-db" && service.type.contains("light")
			})
		}
	})
	println "is ${target} a Light-Instance: ${isLight}"
	isLight
}


def getTargetSystemMappingJson() {
	def configFileLocation = "src/test/resources/TargetSystemMappings.json"
	def targetSystemFile = new File(configFileLocation)
	assert targetSystemFile.exists()
	return new JsonSlurper().parseText(targetSystemFile.text)
}

def getTargetInstance(targetInstanceName,targetSystemMappingJson) {
	println "Fetching targetInstance called ${targetInstanceName} from following JSON: ${targetSystemMappingJson}"
	def res = targetSystemMappingJson.targetInstances.find{it.name == targetInstanceName}
	return res
}