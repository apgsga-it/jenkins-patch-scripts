#!groovy
library 'patch-global-functions'
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

// Mainline
// While mit Start der Pipeline bereits getagt ist 
stage("Entwicklung Installationsbereit Notification") {
	patchfunctions.notify("Entwicklung","Installation", patchConfig)
}

//targets = ['CHTI211','CHPI211']
targets = [
	'CHEI212',
	'CHEI211'] // CHE,3.1 For Testing purposes, needs to externalized
targets.each { target ->
	patchfunctions.targetIndicator(patchConfig,target)
	stage("Approve ${target} Build & Assembly") { patchfunctions.approveBuild(patchConfig) }
	stage("${target} Build" ) { patchfunctions.patchBuilds(patchConfig)  }
	stage("${target} Assembly" ) { patchfunctions.assembleDeploymentArtefacts(patchConfig) }
	stage("${target} Installationsbereit Notification") {
		patchfunctions.notify(target,"Installationsbereit", patchConfig)
	}
	stage("Approve ${target} Installation") { patchfunctions.approveInstallation(patchConfig)	 }
	stage("${target} Installation") { patchfunctions.installDeploymentArtifacts(patchConfig)  }
	stage("${target} Installation Notification") {
		patchfunctions.notify(target,"Installation", patchConfig)
	}
}




