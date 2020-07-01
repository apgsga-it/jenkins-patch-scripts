import hudson.model.*

// TODO (che, 29.10) not very efficient
def coJadasPkgProject() {
	// TODO JHE: Obvisously things to be adapted, basically all parameter which will come from patchConfig, I guess
	lock ("ConcurrentCvsCheckout") {
		coFromBranchCvs('digiflex-jadas-pkg', 'microservice')
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

def assembleJadasPkg() {
	// TODO JHE: Obvisously things to be adapted, basically all parameter which will come from patchConfig, I guess
	sh "./gradlew clean buildRpm -PbomLastRevision=SNAPSHOT -PbaseVersion=1.0 -PinstallTarget=CHEI212 -PrpmReleaseNr=222 -PbuildTyp=SNAPSHOT -Dgradle.user.home=/var/jenkins/gradle/plugindevl --info --stacktrace"
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