import groovy.json.JsonSlurper

def targetSystemFile = new File('src/test/resources/TargetSystemMappings.json')
assert targetSystemFile.exists()
def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
def targetSystemMap = [:]
jsonSystemTargets.targetSystems.each( { target ->
	
	targetSystemMap.put(target.name, [envName:target.name,targetName:target.target, nodes:target.nodes])
})
println targetSystemMap
def target = targetSystemMap.get('Entwicklung')
assert target != null
def phases = targetSystemMap.keySet()
phases.removeElement('Entwicklung')
target = phases.contains('Entwicklung')
assert !target
phases.each { envName ->
	target = targetSystemMap.get(envName)
	assert target != null
	println target
	println "Label for service jadas: " + nodeLabelFor('jadas', target.nodes)
}

def nodeLabelFor(serviceName, nodes) {
	def found = nodes.find { it.serviceName.equals(serviceName)}
	if (found != null) {
		found.label
	} else {
		found
	}
	
}