#!groovy
library 'patch-global-functions'
library 'patch-deployment-functions'
import groovy.json.JsonSlurperClassic

def patchFile = new File("/var/opt/apg-patch-service-server/db/Patch5739.json")
def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
echo patchConfig.toString()
patchConfig.cvsroot = env.CVS_ROOT
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"

// Load Target System Mappings
def targetSystemsMap = patchfunctions.loadTargetsMap()
println "TargetSystemsMap : ${targetSystemsMap} "

[
	'Informatiktest',
].each { envName ->
	target = targetSystemsMap.get(envName)
	assert target != null
	patchfunctions.targetIndicator(patchConfig,target)
	stage("${envName} (${target.targetName}) Build" ) { patchfunctions.buildAndReleaseModulesConcurrent(patchConfig)  }
}