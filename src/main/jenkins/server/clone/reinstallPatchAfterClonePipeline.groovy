#!groovy
package clone
library 'patch-global-functions'
library 'patch-deployment-functions'

import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

properties([
	parameters([
		stringParam(
		defaultValue: "",
		description: 'Parameter',
		name: 'target'
		)
	])
])

stage("reinstallPatchAfterClone") {
	
	node {
		def target = params.target
		echo "Starting to re-install Patch after clone on ${target}"
		
		def patchListFilePath = getPatchListFile(target) 
		
		if(patchListFilePath.exists()) {
			echo "Patch will be re-installed on ${target}"
			def patchList = new JsonSlurperClassic().parseText(patchListFilePath.text)
			echo "Following json has been produced by apsdbcli: ${patchList}"
			def patches = patchList.patchlist
			patches.each{patch ->
				reinstallPatch(patch,target)
			}
		}
		else {
			echo "No patch have to be re-installed on ${target}"
		}
	}
}

def reinstallPatch(def patch, def target) {
	def patchConfig = getPatchConfig(patch,target)
	
	def targetBean = [envName:${target},targetName:patchConfig.installationTarget,typeInd:"T"]
	patchfunctions.targetIndicator(patchConfig,targetBean)
		
	stage("Re-installing patch ${patch} on ${patchConfig.installationTarget}") {
		echo "Starting Build for patch ${patch}"
		node {patchfunctions.patchBuilds(patchConfig)}
		echo "DONE - Build for patch ${patch}"
		echo "Starting Deployment Artefact for patch ${patch}"
		node {patchfunctions.assembleDeploymentArtefacts(patchConfig)}
		echo "DONE - Deployment Artefact for patch ${patch}"
		echo "Starting Installation Artefact for patch ${patch}"
		node {patchDeployment.installDeploymentArtifacts(patchConfig)}
		echo "DONE - Installation Artefact for patch ${patch}"
	}
}

def getPatchConfig(def patch, def target) {
	def patchFile = new File("/var/opt/apg-patch-service-server/db/Patch${patch.toString()}.json")
	assert patchFile.exists() : println ("Patch file /var/opt/apg-patch-service-server/db/Patch${patch.toString()}.json doesn't exist")
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.cvsroot = env.CVS_ROOT
	patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
	patchConfig.dockerBuildExtention = "tar.gz"
	patchConfig.installationTarget = target
}

def getPatchListFile(def target) {
	// We first call apsDbCli in order to produce a file containing the list of patch to be re-installed.
	def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apsdbcli.sh -lpac Informatiktest"
	assert result == 0 : println ("Error while getting list of patch to be re-installed on ${target} for status Informatiktest")
	return new File("/var/opt/apg-patch-cli/patchToBeReinstalled.json")
}