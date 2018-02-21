import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
// Functions
import groovy.transform.EqualsAndHashCode

def tagName(patchConfig) {
	patchConfig.patchTag
}

def targetIndicator(patchConfig, target) {
	def targetInd = '';
	if (target.equals('CHPI211')) {
		targetInd = 'P'
	}
	else {
		targetInd = 'T'
	}
	patchConfig.installationTarget = target
	patchConfig.targetInd = targetInd
}

def mavenVersionNumber(patchConfig,revision) {
	def mavenVersion
	if (revision.equals('SNAPSHOT')) {
		mavenVersion = patchConfig.baseVersionNumber + "." + patchConfig.revisionMnemoPart + "-" + revision
	} else {
		mavenVersion = patchConfig.baseVersionNumber + "." + patchConfig.revisionMnemoPart + "-" + patchConfig.targetInd + '-' + revision
	}
	mavenVersion
}


def approveInstallation(patchConfig) {
	timeout(time:5, unit:'DAYS') {
		userInput = input (id:"Patch${patchConfig.patchNummer}InstallFor${patchConfig.installationTarget}Ok" , message:"Ok for ${patchConfig.installationTarget} Installation?" , submitter: 'svcjenkinsclient,che')
	}
}

def patchBuilds(patchConfig) {
	deleteDir()
	checkoutModules(patchConfig)
	lock("${patchConfig.serviceName}${patchConfig.installationTarget}Build") {
		retrieveRevisions(patchConfig)
		generateVersionProperties(patchConfig)
		buildAndReleaseModules(patchConfig)
		saveRevisions(patchConfig)
	}
}

def retrieveRevisions(patchConfig) {
	def revisionFileName = "${env.JENKINS_HOME}/userContent/PatchPipeline/data/Revisions.json"
	def revisionFile = new File(revisionFileName)
	def currentRevision = [P:1,T:1]
	def lastRevision = [:]
	def revisions = [lastRevisions:lastRevision, currentRevision:currentRevision]
	if (revisionFile.exists()) {
		revisions = new JsonSlurper().parseText(revisionFile.text)
	}
	if (patchConfig.installationTarget.equals("CHPI211")) {
		patchConfig.revision = revisions.currentRevision.P
	} else {
		patchConfig.revision = revisions.currentRevision.T
	}
	patchConfig.lastRevision = revisions.lastRevisions.get(patchConfig.installationTarget,'SNAPSHOT')
}

def saveRevisions(patchConfig) {
	def revisionFileName = "${env.JENKINS_HOME}/userContent/PatchPipeline/data/Revisions.json"
	def revisionFile = new File(revisionFileName)
	def currentRevision = [P:1,T:1]
	def lastRevision = [:]
	def revisions = [lastRevisions:lastRevision, currentRevision:currentRevision]
	if (revisionFile.exists()) {
		revisions = new JsonSlurper().parseText(revisionFile.text)
	}
	if (patchConfig.installationTarget.equals("CHPI211")) {
		revisions.currentRevision.P++
	} else {
		revisions.currentRevision.T++
	}
	revisions.lastRevisions[patchConfig.installationTarget] = patchConfig.revision
	new File(revisionFileName).write(new JsonBuilder(revisions).toPrettyString())
	
}
def buildAndReleaseModules(patchConfig) {
	patchConfig.modules.each { buildAndReleaseModule(patchConfig,it) }
}

def buildAndReleaseModule(patchConfig,module) {
	releaseModule(patchConfig,module)
	buildModule(patchConfig,module)
	updateBom(patchConfig,module)
}


def checkoutModules(patchConfig) {
	def tag = tagName(patchConfig)
	patchConfig.mavenArtifacts.each {
		coFromTagcvs(patchConfig,tag,it.name)
	}
	coFromBranchCvs(patchConfig, 'dm-version-manager')
}

def coFromBranchCvs(patchConfig, moduleName) {
	checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
			[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.microServiceBranch, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]
		], skipChangeLog: false])

}
def coFromTagcvs(patchConfig,tag, moduleName) {
	checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
			[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]
		], skipChangeLog: false])
}

def generateVersionProperties(patchConfig) {
	def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
	def previousVersion = mavenVersionNumber(patchConfig,patchConfig.lastRevision)
	echo "$buildVersion"
	dir ("dm-version-manager") {
		sh "chmod +x ./gradlew"
		sh "./gradlew clean generateVersionProperties publish publishToMavenLocal -Pversion=${previousVersion} -PpublishVersion=${buildVersion} -PworkDir=${WORKSPACE}/work"
	}
}

def releaseModule(patchConfig,module) {
	dir ("${module.name}") {
		echo "Releasing Module : " + module.name + " for Revision: " + patchConfig.revision + " and: " +  patchConfig.revisionMnemoPart
		def mvnCommand = 'mvn clean build-helper:parse-version versions:set -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.\\${parsedVersion.incrementalVersion}.' + patchConfig.revisionMnemoPart + '-' + patchConfig.targetInd + '-' + patchConfig.revision
		echo "${mvnCommand}"
		withMaven( maven: 'apache-maven-3.5.0') { sh "${mvnCommand}" }
	}
}

def buildModule(patchConfig,module) {
	dir ("${module.name}") {
		echo "Building Module : " + module.name + " for Revision: " + patchConfig.revision + " and: " +  patchConfig.revisionMnemoPart
		def mvnCommand = 'mvn deploy'
		echo "${mvnCommand}"
		withMaven( maven: 'apache-maven-3.5.0') { sh "${mvnCommand}" }
	}
}

def updateBom(patchConfig,module) {
	echo "Update Bom for artifact " + module.artifact + " for Revision: " + patchConfig.revision
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	echo "$buildVersion"
	dir ("dm-version-manager") {
		sh "chmod +x ./gradlew"
		sh "./gradlew clean updateVersionProperties publish publishToMavenLocal -Pversion=${buildVersion} -Partifact=${module.artifact} -PworkDir=${WORKSPACE}/work"
	}
}


def assembleDeploymentArtefacts(patchConfig) {
	parallel 'ui-server-assembly':{
		node { assemble(patchConfig, "it21-jadas-service")}
		node { buildDockerImage(patchConfig) }
	}, 'ui-client-assembly':{
		node {assemble(patchConfig, "it21-ui-package")}
	}
}

def buildDockerImage(patchConfig) {
	def extension = patchConfig.dockerBuildExtention
	def artifact = patchConfig.jadasServiceArtifactName
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	def mvnCommand = "mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=${artifact}:${buildVersion}:${extension} -Dtransitive=false"
	echo "${mvnCommand}"
	def mvnCommandCopy = "mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:copy -Dartifact=${artifact}:${buildVersion}:${extension} -DoutputDirectory=./distributions"
	echo "${mvnCommandCopy}"

	def dropName = jadasServiceDropName(patchConfig)
	def dockerBuild = "/opt/apgops/docker/build.sh jadas-service ${WORKSPACE}/distributions/${dropName} ${patchConfig.patchNummer}-${patchConfig.revision}-${BUILD_NUMBER}"
	echo "${dockerBuild}"
	withMaven( maven: 'apache-maven-3.5.0') {
		sh "${mvnCommand}"
		sh "${mvnCommandCopy}"
	}
	sh "${dockerBuild}"
}

def assemble(patchConfig, assemblyName) {
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	echo "Building Assembly ${assemblyName} with version: ${buildVersion} "
	coFromBranchCvs(patchConfig, assemblyName)
	dir ("${assemblyName}") {
		sh "chmod +x ./gradlew"
		sh "./gradlew generateVersionProperties assemble publish -Pversion=${buildVersion}"
	}
}

def installDeploymentArtifacts(patchConfig) {
	lock("${patchConfig.serviceName}${patchConfig.installationTarget}Install") {
		parallel 'ui-server-deployment': {
			node {install(patchConfig,"client","com.affichage.it21:it21gui-dist-zip","zip")}
		}, 'ui-client-deployment': {
			node {install(patchConfig,"docker",patchConfig.jadasServiceArtifactName,patchConfig.dockerBuildExtention) }
		}
	}
}

def install(patchConfig, type, artifact,extension) {
	if (!type.equals("docker")) {
		echo "Don't know how to install ${artifact} of ${type} on ${patchConfig.installationTarget}  : TODO "
		return;
	}

	if(!artifact.equals(patchConfig.jadasServiceArtifactName)) {
		echo "Don't know how to install services apart from jadas-service : TODO"
		return
	}

	def dropName = jadasServiceDropName(patchConfig)
	def dockerDeploy = "/opt/apgops/docker/deploy.sh jadas-service ${patchConfig.patchNummer}-${patchConfig.revision}-${BUILD_NUMBER} ${patchConfig.installationTarget}"
	echo dockerDeploy
	sh "${dockerDeploy}"
}

def jadasServiceDropName(patchConfig) {
	def extension = patchConfig.dockerBuildExtention
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	def artifact = patchConfig.jadasServiceArtifactName
	def pos = artifact.indexOf(':')
	def artifactName = artifact.substring(pos+1)
	return "${artifactName}-${buildVersion}.${extension}"
}

