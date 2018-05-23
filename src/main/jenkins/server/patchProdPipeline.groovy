#!groovy
library 'patch-global-functions'
library 'patch-deployment-functions'
import groovy.json.JsonSlurperClassic
properties([
	parameters([
		stringParam(
		defaultValue: "",
		description: 'Parameter',
		name: 'PARAMETER'
		)
	])
])

// Parameter
def patchConfig = new JsonSlurperClassic().parseText(params.PARAMETER)
echo patchConfig.toString()
patchConfig.cvsroot = "/var/local/cvs/root"
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"

// Load Target System Mappings
def targetSystemsMap = patchfunctions.loadTargetsMap()
println "TargetSystemsMap : ${targetSystemsMap} " 

// Mainline
// While mit Start der Pipeline bereits getagt ist

def target = targetSystemsMap.get('Entwicklung')
stage("${target.envName} (${target.targetName}) Installationsbereit Notification") {
	patchfunctions.notify(target,"Installationsbereit", patchConfig)
}

[
	'Informatiktest',
	'Produktion'
].each { envName ->
	target = targetSystemsMap.get(envName)
	assert target != null
	patchfunctions.targetIndicator(patchConfig,target)
	stage("Approve ${envName} (${target.targetName}) Build & Assembly") { patchfunctions.approveBuild(patchConfig) }
	stage("${envName} (${target.targetName}) Build" ) { patchfunctions.patchBuilds(patchConfig)  }
	stage("${envName} (${target.targetName}) Assembly" ) { patchfunctions.assembleDeploymentArtefacts(patchConfig) }
	stage("${envName} (${target.targetName}) Installationsbereit Notification") {
		patchfunctions.notify(target,"Installationsbereit", patchConfig)
	}
	stage("Approve ${envName} (${target.targetName}) Installation") { patchfunctions.approveInstallation(patchConfig)	 }
	stage("${envName} (${target.targetName}) Installation") { patchDeployment.installDeploymentArtifacts(patchConfig)  }
	stage("${envName} (${target.targetName}) Installation Notification") {
		if(envName.equalsIgnoreCase("produktion")) {
			patchfunctions.mergeDbObjectOnHead(patchConfig)
		}
		patchfunctions.notify(target,"Installation", patchConfig)
	}
	
	stage("${envName} (${target.targetName}) Cleaning up Jenkins workspace") {
		patchfunctions.cleanWorkspace()
	}
}