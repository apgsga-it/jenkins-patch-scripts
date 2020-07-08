import groovy.io.FileType
import groovy.json.JsonSlurperClassic
import groovyjarjarantlr.StringUtils
import hudson.model.*

import javax.swing.JColorChooser

def readPatchFile(patchFilePath) {
	def patchFile = new File(patchFilePath)
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.patchFilePath = patchFilePath
	patchConfig
}

File[] getPatchFilesFrom(File folder) {
	/*
	JHE: Ideally I would use a syntax like the below commented, but I'm only getting one file back. This seems to be a known issue, not clear if really solved or not: https://issues.jenkins-ci.org/browse/JENKINS-46703
	folder.eachFileMatch(~/Patch[0-9]*.json/) {jsonPatchFile ->
		log("Found ${jsonPatchFile.name} -> will be added to the list","getPatchFileNamesFrom")
		fileNames += "${jsonPatchFile.name}:"
	}
	*/
	File[] files = folder.listFiles();
	File[] patchFiles = []
	if (files != null) {
		for (File patchFile : files) {
			if(patchFile.name ==~ ~/Patch[0-9]*.json/) {
				patchFiles << patchFile
			}
		}
	}
	return patchFiles
}

def servicesInPatches(def currentPatchFolderPath) {
	log("Looking for services in patch files, patches from following folder will be parsed: ${currentPatchFolderPath}","servicesInPatches")
	Set<String> serviceNames = []
	File[] patchFiles = getPatchFilesFrom(new File(currentPatchFolderPath))
	for(File patchFile : patchFiles) {
		def patch = readPatchFile(patchFile.path)
		if(!patch.services.isEmpty()) {
			patch.services.each {s ->
				log("${s.serviceName} found within Patch ${patch.patchNummer}", "servicesInPatches")
				serviceNames.add(s.serviceName)
			}
		}
	}
	serviceNames
}

def getPatchFileNamesFrom(def folderPath) {
	log("Searching patch file Names from following folder: ${folderPath}","getPatchFileNamesFrom")
	def fileNames = ""
	File[] patchFiles = getPatchFilesFrom(new File(folderPath))
	for (File patchFile : patchFiles) {
		fileNames += "${patchFile.name}:"
	}
	// Remove last ":"
	return fileNames.substring(0,fileNames.length()-1)
}

def coPackageProjects(def servicesToBeCheckoutOut) {
	log("Packaged project will be checked out for following service: ${servicesToBeCheckoutOut}")
	lock ("ConcurrentCvsCheckout") {
		servicesToBeCheckoutOut.each{s ->
			// TODO JHE: To be verified, we assume a convention to determine pkg project name based on service name
			coFromBranchCvs("${s}-pkg", 'microservice')
		}
	}
}

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

// TODO JHE: Not sure if the target should be taken from patchConfig. But, when assembling, we know for which target we assemble ... not the patch, isn't it?
def assemble(def target, def patchParentDir) {
	log("Patch from ${patchParentDir } for target ${target} will be assembled.")
	def servicesToBeAssembled = servicesInPatches(patchParentDir)
	def patchFiles = getPatchFileNamesFrom(patchParentDir)
	servicesToBeAssembled.each{s ->
		def biggestLastRevision = fetchBiggestLastRevisionFor(s,patchParentDir)
		// TODO JHE: Probably we want to get the service type from TargetSystemMapping.json (or future new file after splitting it up)
		def taskName = s.contains("-ui-") ? "buildZip" : "buildRpm"
		dir("${s}-pkg") {
			// TODO JHE: patchParentDir and patchFileNames harccoded for a test
			def cmd = "./gradlew clean ${taskName} -PpatchParentDir=${patchParentDir} -PpatchFileNames=${patchFiles} -PbomLastRevision=${biggestLastRevision} -PbaseVersion=1.0 -PinstallTarget=${target.toUpperCase()} -PrpmReleaseNr=222 -PbuildTyp=PATCH -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
			log("Assemble cmd: ${cmd}")
			sh cmd
		}
	}
}

def fetchBiggestLastRevisionFor(def serviceName, def patchParentDirPath) {
	def lastRev = 0
	File[] patchFiles = getPatchFilesFrom(new File(patchParentDirPath))
	for(File patchFile : patchFiles) {
		def patch = readPatchFile(patchFile.path)
		if(!patch.services.isEmpty()) {
			patch.services.each{s ->
				if(s.serviceName.equalsIgnoreCase(serviceName) && lastRev < Integer.valueOf(s.lastRevision)) {
					lastRev = Integer.valueOf(s.lastRevision)
					log("New biggest lastRevision = ${lastRev} -> came from Patch ${patch.patchNummer}","fetchBiggestLastRevisionFor")
				}
			}
		}
	}
	log("Biggest lastRevision was : ${lastRev}","fetchBiggestLastRevisionFor")
	String.valueOf(lastRev)
}

def deploy(def servicesToBeDeployed) {
	log("Following services will be deployed using corresponding pkg project: ${servicesToBeDeployed}")
	// TODO JHE: Probably we want to get the service type from TargetSystemMapping.json (or future new file after splitting it up)
	servicesToBeDeployed.each { s ->
		def taskName = s.contains("-ui-") ? "deployZip" : "deployRpm"
		dir("${s}-pkg") {
			sh "./gradlew ${taskName} -PtargetHost=dev-jhedocker.light.apgsga.ch -PbaseVersion=1.0 -PrpmReleaseNr=222 -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
		}
	}
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