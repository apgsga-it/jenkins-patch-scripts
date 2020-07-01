import groovy.io.FileType
import groovy.json.JsonSlurperClassic
import hudson.model.*

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
	// TODO JHE: Obvisously things to be adapted, basically all parameter which will come from patchConfig, I guess
	lock ("ConcurrentCvsCheckout") {
		coFromBranchCvs('digiflex-jadas-pkg', 'microservice')
		coFromBranchCvs('digiflex-it21-ui-pkg', 'microservice')
		coFromBranchCvs('digiflex-web-it21-pkg', 'microservice')
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

def assemble() {
	// TODO JHE: Obvisously things to be adapted, basically all parameter which will come from patchConfig, I guess
	dir("digiflex-jadas-pkg") {
		sh "./gradlew clean buildRpm -PbomLastRevision=SNAPSHOT -PbaseVersion=1.0 -PinstallTarget=CHEI212 -PrpmReleaseNr=222 -PbuildTyp=SNAPSHOT -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
	}
	dir("digiflex-it21-ui-pkg") {
		sh "./gradlew clean buildZip -PbomLastRevision=SNAPSHOT -PbaseVersion=1.0 -PinstallTarget=CHEI212 -PrpmReleaseNr=222 -PbuildTyp=SNAPSHOT -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
	}
	dir("digiflex-web-it21-pkg") {
		sh "./gradlew clean buildRpm -PbomLastRevision=SNAPSHOT -PbaseVersion=1.0 -PinstallTarget=CHEI212 -PrpmReleaseNr=222 -PbuildTyp=SNAPSHOT -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
	}
}

def deploy() {
	// TODO JHE: Obvisously things to be adapted, basically all parameter which will come from patchConfig, I guess
	dir("digiflex-jadas-pkg") {
		sh "./gradlew deployRpm -PtargetHost=dev-jhedocker.light.apgsga.ch -PbaseVersion=1.0 -PrpmReleaseNr=222 -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
	}
	dir("digiflex-it21-ui-pkg") {
		sh "./gradlew deployZip -PtargetHost=dev-jhedocker.light.apgsga.ch -PbaseVersion=1.0 -PrpmReleaseNr=222 -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
	}
	dir("digiflex-web-it21-pkg") {
		sh "./gradlew deployRpm -PtargetHost=dev-jhedocker.light.apgsga.ch -PbaseVersion=1.0 -PrpmReleaseNr=222 -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
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