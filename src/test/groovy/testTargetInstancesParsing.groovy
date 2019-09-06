import groovy.json.JsonSlurper

// JHE: Kind of stupid test, better would naturally be to call function within patchfunctions.groovy.
//	    But for this project, we're not really ready to run function with test configuration.
//		Therefore, for now, the below was just to test the syntax "offline" (without running any pipeline)

println "Starting test ..."
assert isLightInstallation("dev-bsp") : "dev-bsp is a Light !!"
assert !isLightInstallation("CHEI212") : "CHEI212 is not a Light !!"
assert !isLightInstallation("CHTI214") : "CHTI214 is not a Light !!"
assert !isLightInstallation("CHQI211") : "CHQI211 is not a Light !!"
assert !isLightInstallation("CHEI211") : "CHEI211 is not a Light !!"
println "Test done, OK"

def isLightInstallation(target) {
	def isLight = false
	getTargetSystemMappingJson().targetInstances.each ({ targetInstance ->
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