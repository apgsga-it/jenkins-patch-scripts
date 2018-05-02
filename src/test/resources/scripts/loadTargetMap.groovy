import groovy.json.JsonSlurper

def targetSystemFile = new File("src/test/resources/json", "TargetSystemMappings.json")
def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
def targetSystemMap = [:]
jsonSystemTargets.targetSystems.each( { target -> targetSystemMap.put("$target.name", new Expando(envName:"$target.name",target:"${target.target}",targetTypeInd:"${target.targettype}"))})
	println targetSystemMap 
