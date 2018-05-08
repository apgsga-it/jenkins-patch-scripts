import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import hudson.model.*

def loadTargetsMap() {
	def configFileLocation = env.PATCH_SERVICE_COMMON_CONFIG ? env.PATCH_SERVICE_COMMON_CONFIG	: "/var/opt/apg-patch-common/TargetSystemMappings.json"
	def targetSystemFile = new File(configFileLocation)
	assert targetSystemFile.exists()
	def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
	def targetSystemMap = [:]
	jsonSystemTargets.targetSystems.each( { target -> targetSystemMap.put(target.name, [envName:target.name,targetName:target.target,typeInd:target.typeInd])})
	println targetSystemMap
	targetSystemMap
}

def tagName(patchConfig) {
	patchConfig.patchTag
}

// TODO (che, 1.5 ) : we don't really need this anymore , but for the moment
def targetIndicator(patchConfig, target) {
	patchConfig.targetBean = target
	// TODO (che, 1.5) for back ward compatability, must be changed further down the line.
	patchConfig.installationTarget = target.targetName
	patchConfig.targetInd = target.typeInd
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

def approveBuild(patchConfig) {
	timeout(time:5, unit:'DAYS') {
		userInput = input (id:"Patch${patchConfig.patchNummer}BuildFor${patchConfig.installationTarget}Ok" , message:"Ok for ${patchConfig.installationTarget} Build?" , submitter: 'svcjenkinsclient,che')
	}
}

def approveInstallation(patchConfig) {
	timeout(time:5, unit:'DAYS') {
		userInput = input (id:"Patch${patchConfig.patchNummer}InstallFor${patchConfig.installationTarget}Ok" , message:"Ok for ${patchConfig.installationTarget} Installation?" , submitter: 'svcjenkinsclient,che')
	}
}

def patchBuilds(patchConfig) {
	node {
		deleteDir()
		lock("${patchConfig.serviceName}${patchConfig.installationTarget}Build") {
			checkoutModules(patchConfig)
			retrieveRevisions(patchConfig)
			generateVersionProperties(patchConfig)
			buildAndReleaseModules(patchConfig)
			saveRevisions(patchConfig)
		}
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
	patchConfig.revision = revisions.currentRevision[patchConfig.targetInd]
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
	revisions.currentRevision[patchConfig.targetInd]++
	revisions.lastRevisions[patchConfig.installationTarget] = patchConfig.revision
	new File(revisionFileName).write(new JsonBuilder(revisions).toPrettyString())

}
def buildAndReleaseModules(patchConfig) {
	patchConfig.mavenArtifacts.each { buildAndReleaseModule(patchConfig,it) }
}

def buildAndReleaseModule(patchConfig,module) {
	echo "buildAndReleaseModule : " + module.name
	releaseModule(patchConfig,module)
	buildModule(patchConfig,module)
	updateBom(patchConfig,module)
	echo "buildAndReleaseModule : " + module.name

}


def checkoutModules(patchConfig) {
	def tag = tagName(patchConfig)
	patchConfig.mavenArtifacts.each {
		coFromTagcvs(patchConfig,tag,it.name)
	}
	coFromBranchCvs(patchConfig, 'it21-ui-bundle')
}

def coFromBranchCvs(patchConfig, moduleName) {
	checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.microServiceBranch, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]], skipChangeLog: false])

}
def coFromTagcvs(patchConfig,tag, moduleName) {
	checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]], skipChangeLog: false])
}

def generateVersionProperties(patchConfig) {
	def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
	def previousVersion = mavenVersionNumber(patchConfig,patchConfig.lastRevision)
	echo "$buildVersion"
	dir ("it21-ui-bundle") {
		sh "chmod +x ./gradlew"
		sh "./gradlew clean it21-ui-dm-version-manager:publish it21-ui-dm-version-manager:publishToMavenLocal -PsourceVersion=${previousVersion} -PpublishVersion=${buildVersion}"
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
	echo "Update Bom for artifact " + module.artifactId + " for Revision: " + patchConfig.revision
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	echo "$buildVersion"
	dir ("it21-ui-bundle") {
		sh "chmod +x ./gradlew"
		sh "./gradlew clean it21-ui-dm-version-manager:publish it21-ui-dm-version-manager:publishToMavenLocal -PsourceVersion=${buildVersion} -Partifact=${module.groupId}:${module.artifactId}"
	}
}


def assembleDeploymentArtefacts(patchConfig) {
	node {
		coFromBranchCvs(patchConfig, 'it21-ui-bundle')
		assemble(patchConfig, "it21-ui-pkg-server")
		buildDockerImage(patchConfig)
		assemble(patchConfig, "it21-ui-pkg-client")
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
	dir ("it21-ui-bundle") {
		sh "chmod +x ./gradlew"
		sh "./gradlew  ${assemblyName}:assemble ${assemblyName}:publish -PsourceVersion=${buildVersion}"
	}
}


def notify(target,toState,patchConfig) {
	node {
		echo "Notifying ${target} to ${toState}"
		def targetToState = mapToState(target,toState)
		def notCmd = "/opt/apg-patch-cli/bin/apscli.sh -sta ${patchConfig.patchNummer},${targetToState},db"
		echo "Executeing ${notCmd}"
		sh "${notCmd}"
		echo "Executeing ${notCmd} done"
	}

}

def mapToState(target,toState) {
	if (toState.equals("Installationsbereit")) {
		return "${target.envName}${toState}"
	}
	if (toState.equals("Installation")) {
		return "${target.envName}"
	}
	// TODO (che, uge, 04.04.2018 ) Errorhandling
}

def jadasServiceDropName(patchConfig) {
	def extension = patchConfig.dockerBuildExtention
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	def artifact = patchConfig.jadasServiceArtifactName
	def pos = artifact.indexOf(':')
	def artifactName = artifact.substring(pos+1)
	return "${artifactName}-${buildVersion}.${extension}"
}

def cleanWorkspaceAndMovejob(patchConfig) {
	node {
		// Clean workspace
		cleanWs()
						
		// Rename and move jobs
		def JOB_PATTERN = "Patch${patchConfig.patchNummer}"; //find all jobs starting with "PatchXXXX".
		def NEW_PART = "PROD_"
		(Hudson.instance.items.findAll { job -> job.name =~ JOB_PATTERN }).each { job_to_update ->
			def NEW_JOB_NAME = NEW_PART + job_to_update.name
			echo("Updating job " + job_to_update.name);
			echo("New name: " + NEW_JOB_NAME);
			job_to_update.renameTo(NEW_JOB_NAME);
			echo("Updated name: " + job_to_update.name);
				
			echo("Now moving ${NEW_JOB_NAME} under productive Patch view")
			def productivePatchView = hudson.model.Hudson.instance.getView('ProductivePatches')
			productivePatchView.doAddJobToView(NEW_JOB_NAME)
			// JHE (08.05.2018): the below is not necessary. Patches view list job based on Regex, not by direclty including jobs.
			//def patchView = hudson.model.Hudson.instance.getView('Patches')
			//myView2.doRemoveJobFromView(NEW_JOB_NAME)
		}
	}
}