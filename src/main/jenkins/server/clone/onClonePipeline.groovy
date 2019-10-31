package clone
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

def source = env.source
def target = env.target

patchfunctions.log("Parameter ... source = ${source} , target = ${target}")

stage("onclone") {
	
	stage("preProcessVerification") {
		
		// JHE/UGE (11.10.2018): We explicitly want to test against CHPI211, otherwise we can't test the onClone before going live.
		assert !target.equalsIgnoreCase("chpi211") : patchfunctions.log("Target parameter can't be the target define as production!")
		
		// JHE/UGE (11.10.2018): We consider chqi211 same as chpi211 (from source point of view only)
		if(source.equalsIgnoreCase("chqi211")) {
			source = "CHPI211"
		}

		// As source environment, only the PROD defined environment is allowed
		def status = getStatusName(source)
		if(status != null) { 
			assert status.toString().equalsIgnoreCase("produktion") : patchfunctions.log("When cloning, source parameter can only be the one define as production target.") 
		}
				
	}
	
	stage("cleanArtifactory") {
		node {
			patchfunctions.log("Cleaning Artifactory for revisions build for target ${target}")
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -cr ${target}"
			assert result == 0 : patchfunctions.log("Error while clean Artifactory revision for target ${target}")
		}
	}
	
	stage("resetRevision") {
		node {
			patchfunctions.log("Revision will be reset for target ${target}, and reset with basis from source ${source}")
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apsrevcli.sh -rr ${source},${target}"
			assert result == 0 : patchfunctions.log("Error while resetting revision for ${target}")
		}
	}
	
	stage("startReinstallPatch") {
		node { 
			patchfunctions.log("Starting to re-install Patch after clone on ${target}")
			def File patchListFile = getPatchListFile(target)
			assert patchListFile.exists() : patchfunctions.log("${patchListFile.getPath()} doesn't exist, cannot determine if patch needs to be re-install or not")
			patchfunctions.log("Patch will be re-installed on ${target}")
			def patchList = new JsonSlurperClassic().parseText(patchListFile.text)
			patchfunctions.log("Following json has been produced by apsdbcli: ${patchList}")
			def patches = patchList.patchlist
			patches.size() > 0 ? patches.each{patch -> reinstallPatch(patch,target)} : "No patch have to be re-installed on ${target}"
		}
	}
}

private def getStatusName(def env) {
	def targetSystemFile = new File("/etc/opt/apg-patch-common/TargetSystemMappings.json")
	assert targetSystemFile.exists() : patchfunctions.log("/etc/opt/apg-patch-common/TargetSystemMappings.json doesn't exist or is not accessible!")
	def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
	
	// By default, if the target is not part of the "standard workflow" (Informatiktest,Anwendertest,Produktion), we assume the basis for patch installation is "Produktion"
	def status = "Produktion"
	
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
		patchfunctions.log("patchConfig.jadasInstallationNodeLabel set with ${patchConfig.jadasInstallationNodeLabel}")
		patchfunctions.log(patchConfig.mavenLocalRepo)
			
		stage("Re-installing patch ${patch} on ${patchConfig.currentTarget}") {
			patchfunctions.stage(targetBean,"Installationsbereit",patchConfig,"Build", patchfunctions.&patchBuildsConcurrent)
			patchfunctions.stage(targetBean,"Installationsbereit",patchConfig,"Assembly", patchfunctions.&assembleDeploymentArtefacts)
			patchfunctions.stage(targetBean,"Installation",patchConfig,"Install", patchDeployment.&installDeploymentArtifacts)
		}
	}
	else {
		patchfunctions.log("Patch ${patch} has not been re-installed because the corresponding JSON file has not been found.")
	}
}

private def patchFileExists(def patch) {
	return new File("${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json").exists()
}

private def getPatchConfig(def patch, def target) {
	patchfunctions.log("Getting patchConfig for patch ${patch} on target ${target}...")
	def patchFile = new File("${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json")
	assert patchFile.exists() : patchfunctions.log("Patch file ${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json doesn't exist")
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.cvsroot = env.CVS_ROOT
	patchConfig.currentTarget = target
	patchConfig.patchFilePath = "${env.PATCH_DB_FOLDER}${env.PATCH_FILE_PREFIX}${patch.toString()}.json"
	patchfunctions.log("patchConfig for patch ${patch} : ${patchConfig}")
	return patchConfig
}

private def getPatchListFile(def target) {
	// We first call apsDbCli in order to produce a file containing the list of patch to be re-installed.
	def status = getStatusName(target)
	def cmd = "/opt/apg-patch-cli/bin/apsdbcli.sh -lpac ${status}"
	def result = sh (returnStdout: true, script: cmd).trim()
	assert result : patchfunctions.log("Error while getting list of patch to be re-installed on ${target}")
	return new File("/var/opt/apg-patch-cli/patchToBeReinstalled${status}.json")
}