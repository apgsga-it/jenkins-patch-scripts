package clone
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

properties([
	parameters([
		stringParam(
			defaultValue: "",
			description: 'Parameter',
			name: 'source'
			),
		stringParam(
			defaultValue: "",
			description: 'Parameter',
			name: 'target'
		)
	])
])

def source = params.source
def target = params.target

println "Parameter ... source = ${source} , target = ${target}"

stage("onclone") {
	
	stage("preProcessVerification") {
		
		// JHE/UGE (11.10.2018): We explicitly want to test against CHPI211, otherwise we can't test the onClone before going live.
		assert !target.equalsIgnoreCase("chpi211") : println("Target parameter can't be the target define as production!")
		
		// JHE/UGE (11.10.2018): We consider chqi211 same as chpi211 (from source point of view only)
		if(source.equalsIgnoreCase("chqi211")) {
			source = "CHPI211"
		}

		// As source environment, only the PROD defined environment is allowed
		def status = getStatusName(source)
		if(status != null) { 
			assert status.toString().equalsIgnoreCase("produktion") : println("When cloning, source parameter can only be the one define as production target.") 
		}
				
	}
	
	stage("cleanArtifactory") {
		node {
			echo "Cleaning Artifactory for revisions build for target ${target}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -cr ${target}"
			assert result == 0 : println ("Error while clean Artifactory revision for target ${target}")
		}
	}
	
	stage("resetRevision") {
		node {
			echo "Revision will be reset for target ${target}, and reset with basis from source ${source}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apsrevcli.sh -rr ${source},${target}"
			assert result == 0 : println ("Error while resetting revision for ${target}")
		}
	}
	
	stage("startReinstallPatch") {
		node { 
			echo "Starting to re-install Patch after clone on ${target}"
			def File patchListFile = getPatchListFile(target)
			assert patchListFile.exists() : println ("${patchListFile.getPath()} doesn't exist, cannot determine if patch needs to be re-install or not")
			echo "Patch will be re-installed on ${target}"
			def patchList = new JsonSlurperClassic().parseText(patchListFile.text)
			echo "Following json has been produced by apsdbcli: ${patchList}"
			def patches = patchList.patchlist
			patches.size() > 0 ? patches.each{patch -> reinstallPatch(patch,target)} : "No patch have to be re-installed on ${target}"
		}
	}
}

private def getStatusName(def env) {
	def targetSystemFile = new File("/etc/opt/apg-patch-common/TargetSystemMappings.json")
	assert targetSystemFile.exists() : println ("/etc/opt/apg-patch-common/TargetSystemMappings.json doesn't exist or is not accessible!")
	def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
	def status
	
	jsonSystemTargets.targetSystems.each{ targetSystem ->
		if(targetSystem.target.equalsIgnoreCase(env)) {
			status = targetSystem.name
		}
	}
	
	return status
}

private def reinstallPatch(def patch, def target) {
	
	if(patchFileExists(patch)) {
	
		def patchConfig = getPatchConfig(patch,target)
		
		def defaultNodes = [[label:env.DEFAULT_JADAS_REINSTALL_PATCH_NODE,serviceName:"jadas"]]
		def targetBean = [envName:target,targetName:patchConfig.currentTarget,nodes:defaultNodes]
		patchfunctions.saveTarget(patchConfig,targetBean)
		patchfunctions.mavenLocalRepo(patchConfig)
		patchConfig.jadasInstallationNodeLabel = patchfunctions.serviceInstallationNodeLabel(targetBean,"jadas")
		echo "patchConfig.jadasInstallationNodeLabel set with ${patchConfig.jadasInstallationNodeLabel}"
		println patchConfig.mavenLocalRepo
			
		stage("Re-installing patch ${patch} on ${patchConfig.currentTarget}") {
			echo "Starting Build for patch ${patch}"
			node {patchfunctions.patchBuildsConcurrent(patchConfig)}
			echo "DONE - Build for patch ${patch}"
			echo "Starting assemble Artefact for patch ${patch}"
			node {patchfunctions.assembleDeploymentArtefacts(patchConfig)}
			echo "DONE - assemble Artefact for patch ${patch}"
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

private def patchFileExists(def patch) {
	return new File("${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json").exists()
}

private def getPatchConfig(def patch, def target) {
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

private def getPatchListFile(def target) {
	// We first call apsDbCli in order to produce a file containing the list of patch to be re-installed.
	def status = getStatusName(target)
	def cmd = "/opt/apg-patch-cli/bin/apsdbcli.sh -lpac ${status}"
	def result = sh (returnStdout: true, script: cmd).trim()
	assert result : println ("Error while getting list of patch to be re-installed on ${target}")
	return new File("/var/opt/apg-patch-cli/patchToBeReinstalled.json")
}