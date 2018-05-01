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
stage("${target.envName} ${target.name}) Installationsbereit Notification") {
	patchfunctions.notify(target,"Installationsbereit", patchConfig)
}

[
	'Integrationstest',
	'Produktion'
].each { envName ->
	target = targetSystemsMap.get(envName)
	patchfunctions.targetIndicator(patchConfig,target)
	stage("Approve ${envName} (${target.name}) Build & Assembly") { patchfunctions.approveBuild(patchConfig) }
	stage("${envName} (${target.name}) Build" ) { patchfunctions.patchBuilds(patchConfig)  }
	stage("${envName} (${target.name}) Assembly" ) { patchfunctions.assembleDeploymentArtefacts(patchConfig) }
	stage("${envName} (${target.name}) Installationsbereit Notification") {
		patchfunctions.notify(target,"Installationsbereit", patchConfig)
	}
	stage("Approve ${envName} (${target.name}) Installation") { patchfunctions.approveInstallation(patchConfig)	 }
	stage("${envName} (${target.name}) Installation") { patchDeployment.installDeploymentArtifacts(patchConfig)  }
	stage("${envName} (${target.name}) Installation Notification") {
		patchfunctions.notify(target,"Installation", patchConfig)
	}
}




