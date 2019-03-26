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
	
	if(patchFileExists(patch)) {
	
		def patchConfig = getPatchConfig(patch,target)
		
		def defaultNodes = [[label:env.DEFAULT_JADAS_REINSTALL_PATCH_NODE,serviceName:"jadas"]]
		def targetBean = [envName:target,targetName:patchConfig.currentTarget,nodes:defaultNodes]
		patchfunctions.saveTarget(patchConfig,targetBean)
		patchfunctions.mavenLocalRepo(patchConfig)
		patchConfig.jadasInstallationNodeLabel = patchfunctions.jadasInstallationNodeLabel(targetBean)
		echo "patchConfig.jadasInstallationNodeLabel set with ${patchConfig.jadasInstallationNodeLabel}"
		println patchConfig.mavenLocalRepo
			
		stage("Re-installing patch ${patch} on ${patchConfig.currentTarget}") {
			echo "Starting Build for patch ${patch}"
			node {patchfunctions.patchBuildsConcurrent(patchConfig)}
			echo "DONE - Build for patch ${patch}"
			echo "Starting Deployment Artefact for patch ${patch}"
			node {patchfunctions.assembleDeploymentArtefacts(patchConfig)}
			echo "DONE - Deployment Artefact for patch ${patch}"
			echo "Starting old Style installation for patch ${patch}"
			node {patchDeployment.installOldStyle(patchConfig)}
			echo "DONE - Starting old Style installation for patch ${patch}"
			echo "Starting Installation Artefact for patch ${patch}"
			node {patchDeployment.installDeploymentArtifacts(patchConfig)}
			echo "DONE - Installation Artefact for patch ${patch}"
		}
	}
	else {
		echo "Patch ${patch} has not been re-installed because the corresponding JSON file has not been found."
	}
}

def patchFileExists(def patch) {
	return new File("${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json").exists()
}

def getPatchConfig(def patch, def target) {
	echo "Getting patchConfig for patch ${patch} on target ${target}..."
	def patchFile = new File("${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json")
	assert patchFile.exists() : println ("Patch file ${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json doesn't exist")
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.cvsroot = env.CVS_ROOT
	patchConfig.currentTarget = target
	patchConfig.patchFilePath = "${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json"
	echo "patchConfig for patch ${patch} : ${patchConfig}"
	return patchConfig
}

def getPatchListFile(def target) {
	// We first call apsDbCli in order to produce a file containing the list of patch to be re-installed.
	def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apsdbcli.sh -lpac Informatiktest"
	assert result == 0 : println ("Error while getting list of patch to be re-installed on ${target} for status Informatiktest")
	return new File("/var/opt/apg-patch-cli/patchToBeReinstalled.json")
}