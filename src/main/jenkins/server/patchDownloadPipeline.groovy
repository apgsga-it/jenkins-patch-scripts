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
// TODO  (che, 9.7) When JENKINS-27413 is resolved
// Passing Patch File Path , because of JAVA8MIG-395 / JENKINS-27413
def patchFile = new File(params.PARAMETER)
def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
echo patchConfig.toString()
patchConfig.cvsroot = env.CVS_ROOT
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"

// Mainline
def target = [envName:"Download",targetName:patchConfig.installationTarget,typeInd:"T"]
patchfunctions.targetIndicator(patchConfig,target)
stage("${target.targetName} Build & Assembly") {
	stage("${target.targetName} Build" ) {
		node {patchfunctions.patchBuildsConcurrent(patchConfig)}
	}
	stage("${target.targetName} Assembly" ) {
		node {patchfunctions.assembleDeploymentArtefacts(patchConfig)}
	}
}
stage("${target.targetName} Installation") {
	node {patchDeployment.installDeploymentArtifacts(patchConfig)}
}