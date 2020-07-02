import groovy.io.FileType
import groovy.json.JsonSlurperClassic
import hudson.model.*

import javax.swing.JColorChooser

def readPatchFile(patchFilePath) {
	def patchFile = new File(patchFilePath)
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.patchFilePath = patchFilePath
	patchConfig
}

def servicesInPatches(def currentPatchFolderPath) {
	log("Patches from following folder will be parsed: ${currentPatchFolderPath}","servicesInPatches")
	Set<String> serviceSames = []
	def workFolder = new File(currentPatchFolderPath)
	workFolder.eachFileRecurse(FileType.FILES) {jsonPatchFile ->
		def p = readPatchFile(jsonPatchFile.path)
		if(!p.services.isEmpty()) {
			p.services.each {s ->
				log("${s.serviceName} found within Patch ${p.patchNummer}", "servicesInPatches")
				serviceSames.add(s.serviceName)
			}
		}
	}
	serviceSames
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
def assemble(def servicesToBeAssembled, def target) {
	log("Following service will be assembled using corresponding pkg project: ${servicesToBeAssembled} for target ${target}")
	servicesToBeAssembled.each{s ->
		// TODO JHE: Probably we want to get the service type from TargetSystemMapping.json (or future new file after splitting it up)
		def taskName = s.contains("-ui-") ? "buildZip" : "buildRpm"
		dir("${s}-pkg") {
			// TODO JHE: patchParentDir and patchFileNames harccoded for a test
			sh "./gradlew clean ${taskName} -PpatchParentDir=/var/opt/apg-patch-service-server/db -PpatchFileNames=Patch7000.json -PbomLastRevision=SNAPSHOT -PbaseVersion=1.0 -PinstallTarget=${target.toUpperCase()} -PrpmReleaseNr=222 -PbuildTyp=PATCH -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
		}
	}
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