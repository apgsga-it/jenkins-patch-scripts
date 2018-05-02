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

// Mainline
// TODO (che, 1.5 ) : probably should also be read from targets configuration
// Same contract as with patchProdPipeline
def target = new Expando(envName:"Download",targetName:patchConfig.installationTarget,typeInd:"T")
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
	node {patchDeployment.installDeploymentArtifacts(patchConfig)}
}