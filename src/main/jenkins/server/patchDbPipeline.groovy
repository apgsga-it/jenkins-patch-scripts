#!groovy
// TODO JHE: Not sure that we'll really need these libs ... maybe a new one ?
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

// TODO JHE: Not sure we'll need these 2 properties ...
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"

//Main line
def target = patchConfig.installationTarget
patchfunctions.targetIndicator(patchConfig,target)
stage("${target} Build & Assembly") {
	stage("${target} Build" ) {
		echo "Building object for DB ... TODO ..."
	}
	stage("${target} Assembly" ) {
		echo "Assembly object for DB ... TODO ..."
	}
}
stage("${target} Installation") {
	echo "Installing DB Object ... TODO ..."
}