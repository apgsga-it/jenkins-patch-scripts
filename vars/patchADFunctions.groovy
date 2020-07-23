import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic
import hudson.model.*

def readPatchFile(patchFilePath) {
	def patchFile = new File(patchFilePath)
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.patchFilePath = patchFilePath
	patchConfig
}

File[] getPatchFilesFrom(String stashName) {
	/*
	JHE: Ideally I would use a syntax like the below commented, but I'm only getting one file back. This seems to be a known issue, not clear if really solved or not: https://issues.jenkins-ci.org/browse/JENKINS-46703
	folder.eachFileMatch(~/Patch[0-9]*.json/) {jsonPatchFile ->
		log("Found ${jsonPatchFile.name} -> will be added to the list","getPatchFileNamesFrom")
		fileNames += "${jsonPatchFile.name}:"
	}
	*/

	List<File> patchFiles = new ArrayList<>()
	dir(stashName) {
		unstash stashName
		File[] files = new File(pwd()).listFiles();
		if (files != null) {
			log("Searching for Patch within ${pwd()}/${stashName}","getPatchFilesFrom")
			for (File patchFile : files) {
				if(patchFile.name ==~ ~/Patch[0-9]*.json/) {
					log("Patch ${patchFile.name} found and added to the list","getPatchFilesFrom")
					patchFiles.add(patchFile)
				}
			}
		}
	}
	return patchFiles
}

def servicesInPatches(def stashName) {
	log("Getting services names from stashed patch Files. Stash name = ${stashName}")
	Set<String> serviceNames = []
	List<File> patchFiles = getPatchFilesFrom(stashName)
	patchFiles.each { patchFile ->
		def patch = readPatchFile(patchFile.path)
		if (!patch.services.isEmpty()) {
			patch.services.each { s ->
				log("${s.serviceName} found within Patch ${patch.patchNummer}", "servicesInPatches")
				serviceNames.add(s.serviceName)
			}
		}
	}
	log("serviceNames in Patches : ${serviceNames}","servicesInPatches")
	serviceNames
}

/*
def coPackageProjects(def servicesToBeCheckoutOut) {
	log("Packaged project will be checked out for following service: ${servicesToBeCheckoutOut}")
	lock ("ConcurrentCvsCheckout") {
		servicesToBeCheckoutOut.each{s ->
			// TODO JHE: To be verified, we assume a convention to determine pkg project name based on service name
			coFromBranchCvs("${s}-pkg", 'microservice')
		}
	}
}
*/

def coFromBranchCvs(moduleName, type) {
	// TODO JHE: Obvisously things to be adapted, basically all parameter which will come from patchConfig, I guess
	def cvsBranch = "apg_vaadin_1_0_x_digiflex"
	if(type.equals("db")) {
		cvsBranch = "toBeDetermine"
	}
	def cvsroot = env.CVS_ROOT
	def callBack = benchmark()
	def duration = callBack {
		checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
				[compressionLevel: -1, cvsRoot: cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
						[location: [$class: 'BranchRepositoryLocation', branchName: cvsBranch, useHeadIfNotFound: false],  modules: [
								[localName: moduleName, remoteName: moduleName]
							]]
					]]
			], skipChangeLog: false])
	}
	log("Checkout of ${moduleName} took ${duration} ms","coFromBranchCvs")
}

/*
def assembleAndDeploy(def target, def stashName, def targetSystemMappingFile, def serviceInPatches) {
	dir(stashName) {
		unstash stashName
	}
	log("Following services will be assemble for target ${target} : ${serviceInPatches}","assembleAndDeploy")
	serviceInPatches.each{s ->
		def taskNames = serviceTypeFor(s,target,targetSystemMappingFile).equalsIgnoreCase("linuxbasedwindowsfilesystem") ? "buildZip deployZip" : "buildRpm deployRpm"
		def deployTarget = getInstallTargetFor(s,target,targetSystemMappingFile)
		// TODO JHE: Either serviceName in JSON will be the packager name, or we have to apply such a convention
		dir("${s}-pkg") {
			// TODO JHE: Configure gradle.user.home from external place
			def cmd = "./gradlew clean ${taskNames} -PtargetHost=${deployTarget} -PbuildTyp=CLONED -PbaseVersion=1.0 -PinstallTarget=${target.toUpperCase()} -PcloneTargetPath=${env.WORKSPACE}/${stashName} -Dgradle.user.home=/var/jenkins/gradle/home --info --stacktrace"
			log("Assemble cmd: ${cmd}")
			sh cmd
		}
	}
}
 */

def assembleAndDeploy(def target, def stashName, def targetSystemMappingFile) {
	dir(stashName) {
		unstash stashName
	}
	def serviceInPatches = servicesInPatches(stashName)
	log("Following services will be assemble for target ${target} : ${serviceInPatches}","assembleAndDeploy")
	serviceInPatches.each{s ->
		coFromBranchCvs("${s}-pkg", 'microservice')
		def taskNames = serviceTypeFor(s,target,targetSystemMappingFile).equalsIgnoreCase("linuxbasedwindowsfilesystem") ? "buildZip deployZip" : "buildRpm deployRpm"
		def deployTarget = getInstallTargetFor(s,target,targetSystemMappingFile)
		// TODO JHE: Either serviceName in JSON will be the packager name, or we have to apply such a convention
		dir("${s}-pkg") {
			// TODO JHE: Configure gradle.user.home from external place
			def cmd = "./gradlew clean ${taskNames} -PtargetHost=${deployTarget} -PbuildTyp=CLONED -PbaseVersion=1.0 -PinstallTarget=${target.toUpperCase()} -PcloneTargetPath=${env.WORKSPACE}/${stashName} -Dgradle.user.home=/var/jenkins/gradle/home --info --stacktrace"
			log("Assemble cmd: ${cmd}")
			sh cmd
		}
	}
}

// TODO JHE: verify if list of parameters makes sense
def install(def target, def workDir, def targetSystemMappingFile) {
	log("Installation will be done for Patches located in ${workDir} for target ${target}","install")
	def serviceToBeInstalled = servicesInPatches(workDir)
	serviceToBeInstalled.each{ s ->
		def taskName = serviceTypeFor(s,target,targetSystemMappingFile).equalsIgnoreCase("linuxbasedwindowsfilesystem") ? "installZip" : "installRpm"
		def installTarget = getInstallTargetFor(s,target,targetSystemMappingFile)
		dir("${s}-pkg") {
			// TODO JHE: Configure gradle.user.home from external place
			def cmd = "./gradlew clean ${taskName} -PtargetHost=${installTarget} -Dgradle.user.home=/var/jenkins/gradle/home --info --stacktrace"
			log("install cmd: ${cmd}")
			sh cmd
		}
	}
}

def serviceTypeFor(serviceName,target, targetSystemMappingFile) {
	def targetInstances = loadTargetInstances(targetSystemMappingFile)
	return targetInstances."${target}".find{service -> service.name.equalsIgnoreCase(serviceName)}.type
}

def getInstallTargetFor(serviceName, target, targetSystemMappingFile) {
	def targetInstances = loadTargetInstances(targetSystemMappingFile)
	return targetInstances."${target}".find{service -> service.name.equalsIgnoreCase(serviceName)}.host
}

def loadTargetInstances(targetSystemMappingAsText) {
	def targetInstances = [:]
	def targetSystemMappingAsJson = new JsonSlurper().parseText(targetSystemMappingAsText)
	targetSystemMappingAsJson.targetInstances.each( {targetInstance ->
		targetInstances.put(targetInstance.name,targetInstance.services)
	})
	targetInstances
}

def benchmark() {
	def benchmarkCallback = { closure ->
		start = System.currentTimeMillis()
		closure.call()
		now = System.currentTimeMillis()
		now - start
	}
	benchmarkCallback
}

// Used in order to have Datetime info in our pipelines
def log(msg,caller) {
	def dt = "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}"
	def logMsg = caller != null ? "(${caller}) ${dt}: ${msg}" : "${dt}: ${msg}"
	echo logMsg
}

// Used in order to have Datetime info in our pipelines
def log(msg) {
	log(msg,null)
}