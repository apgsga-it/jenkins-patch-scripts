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
def target = patchConfig.installationTarget
patchfunctions.targetIndicator(patchConfig,target)
stage("${target} Build & Assembly") {
	stage("${target} Build" ) {
		node {patchfunctions.patchBuilds(patchConfig)}
	}
	stage("${target} Assembly" ) {
		node {patchfunctions.assembleDeploymentArtefacts(patchConfig)}
	}
}
stage("${target} Installation") {
	node {patchfunctions.installDeploymentArtifacts(patchConfig)}
}