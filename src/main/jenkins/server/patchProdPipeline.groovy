#!groovy
library 'patch-global-functions'​
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

//targets = ['CHTI211','CHPI211']
targets = ['CHEI212','CHEI211'] // CHE,3.1 For Testing purposes
targets.each { target ->
	patchfunctions.targetIndicator(patchConfig,target)
	stage("${target} Build & Assembly") {
		stage("${target} Build" ) {
			node {patchfunctions.patchBuilds(patchConfig) }
		}
		stage("${target} Assembly" ) {
			patchfunctions.assembleDeploymentArtefacts(patchConfig)
		}
	}
	stage("Approve ${target} Installation") {
			patchfunctions.approveInstallation(patchConfig)	
	}
	stage("${target} Installation") {
		patchfunctions.installDeploymentArtifacts(patchConfig) 
	}
}




