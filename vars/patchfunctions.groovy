import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
// Functions
import groovy.transform.EqualsAndHashCode

def tagName(patchConfig) {
	patchConfig.patchTag
}

def targetIndicator(patchConfig, target) {
	def targetInd = '';
	// TODO (che, 4.4.2018) : needs to be configurable
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
	lock("${patchConfig.serviceName}${patchConfig.installationTarget}Build") {
		checkoutModules(patchConfig)
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
		withMaven( maven: 'apache-maven-3.5.0') { sh "mvn help:effective-settings" }
		withMaven( maven: 'apache-maven-3.5.0') { sh "mvn help:effective-pom" }
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

def installDeploymentArtifacts(patchConfig) {
	lock("${patchConfig.serviceName}${patchConfig.installationTarget}Install") {
		parallel 'ui-client-deployment': {
			node {install(patchConfig,"client","it21gui-dist-zip","zip")}
		}, 'ui-server-deployment': {
			node {install(patchConfig,"docker",patchConfig.jadasServiceArtifactName,patchConfig.dockerBuildExtention) }
		}
	}
}

def notify(target,toState,patchConfig) {
	echo "Notifying ${target} to ${toState}"
	def targetToState = mapToState(target,toState)
	def notCmd = "/opt/apgops/jenkins_persist_patch_status.sh ${patchConfig.patchNummer} ${targetToState}"
	echo "Executeing ${notCmd}"
	// sh ${notCmd}
	
}

def mapToState(target,toState) {
	// TODO (che, uge, 04.04.2018) : needs to be configurable
	// TODO (che, uge, 04.04.2018) : first Mapping needs to be Verified
	if (target.equals('CHEI211')) {
		return "Produktins${toState}"
	}
	if (target.equals('CHEI212')) {
		return "Informatiktest${toState}"
	}
	// TODO (che, uge, 04.04.2018 ) Errorhandling 
}

def install(patchConfig, type, artifact,extension) {
	if (!type.equals("docker")) {
		
		if(!patchConfig.installationTarget.equalsIgnoreCase("CHEI212")) {
			echo "GUI can currently only be installed on CHEI212. ${patchConfig.installationTarget} not supported yet."
			return
		}

		installGUI(patchConfig,artifact,extension)				
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

def installGUI(patchConfig,artifact,extension) {
	node("apg-jdv-e-001") {
		// Will probably be removed, but for now we need to initiate the connection on \\gui-chei212.apgsga.ch ...
		powershell("invoke-expression -Command \"C:\\Software\\initAndClean\\init_install_${patchConfig.installationTarget}_it21gui.ps1\"")
		
		def artifactoryServer = initiateArtifactoryConnection()
		
		def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
		def zip = "${artifact}-${buildVersion}.${extension}"
		
		//TODO JHE: here we should probably pass the repo type as well -> snapshot or relaease, althought it might always be relaease...
		downloadGuiZipToBeInstalled(artifactoryServer,zip)
		
		def extractedFolderName = guiExtractedFolderName()
		
		extractZip(zip,patchConfig,extractedFolderName)
		renameExtractedZip(patchConfig,extractedFolderName)
		copyOpsResources(patchConfig,extractedFolderName)
		
		// Will probably be removed, but we call a script to reset the connection which was initiated on \\gui-chei212.apgsga.ch
		powershell("invoke-expression -Command \"C:\\Software\\initAndClean\\clean_install_${patchConfig.installationTarget}_it21gui.ps1\"")
	}
}

def guiExtractedFolderName() {
	def currentDateAndTime = new Date().format('yyyyMMddHHmmss')
	def extractedFolderName = "java_gui_${currentDateAndTime}"
	return extractedFolderName
}

def jadasServiceDropName(patchConfig) {
	def extension = patchConfig.dockerBuildExtention
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	def artifact = patchConfig.jadasServiceArtifactName
	def pos = artifact.indexOf(':')
	def artifactName = artifact.substring(pos+1)
	return "${artifactName}-${buildVersion}.${extension}"
}

def downloadGuiZipToBeInstalled(artifactoryServer,zip) {
	def downloadSpec = """{
              "files": [
                    {
                      "pattern": "releases/*${zip}",
		 			  "target": "download/"
	   				}
			 ]
	}"""
	artifactoryServer.download(downloadSpec)
}

def initiateArtifactoryConnection() {
	def server = Artifactory.server 'artifactory4t4apgsga' // prerequisite: needs to be configured on Jenkins

	//TODO JHE(05.04.2018) : Where should we best store the credentialsId ?	
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: '05e78d62-4ce3-4a9f-bab2-2c0bf5806954',
		usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

		server.username = "${USERNAME}"
		server.password = "${PASSWORD}"
	}
	
	return server
}

def extractZip(downloadedZip,patchConfig,extractedFolderName) {
	def files = findFiles(glob: "**/${downloadedZip}")
	unzip zipFile: "${files[0].path}", dir: "\\\\gui-${patchConfig.installationTarget}.apgsga.ch\\it21_${patchConfig.installationTarget}\\getting_extracted_${extractedFolderName}"
}

def renameExtractedZip(patchConfig,extractedFolderName) {
	fileOperations ([
		folderRenameOperation(source: "\\\\gui-${patchConfig.installationTarget}.apgsga.ch\\it21_${patchConfig.installationTarget}\\getting_extracted_${extractedFolderName}", destination: "\\\\gui-${patchConfig.installationTarget}.apgsga.ch\\it21_${patchConfig.installationTarget}\\${extractedFolderName}")
	])
}

def copyOpsResources(patchConfig,extractedFolderName) {
	dir("C:\\config\\${patchConfig.installationTarget}\\it21-gui") {
		fileOperations ([
			fileCopyOperation(flattenFiles: true, excludes: '', includes: '*.properties', targetLocation: "\\\\gui-${patchConfig.installationTarget}.apgsga.ch\\it21_${patchConfig.installationTarget}\\${extractedFolderName}\\conf")
		])
	}
}
