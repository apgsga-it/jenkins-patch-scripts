import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic
import hudson.model.*

def benchmark() {
	def benchmarkCallback = { closure ->
		start = System.currentTimeMillis()
		closure.call()
		now = System.currentTimeMillis()
		now - start
	}
	benchmarkCallback
}

def readPatchFile(patchFilePath) {
	def patchFile = new File(patchFilePath)
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.patchFilePath = patchFilePath
	patchConfig
}

def initPatchConfig(patchConfig, params) {
	patchConfig.cvsroot = env.CVS_ROOT
	patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
	patchConfig.dockerBuildExtention = "tar.gz"
	patchConfig.patchFilePath = params.PARAMETER
	patchConfig.redo = params.RESTART.equals("TRUE")
}

def savePatchConfigState(patchConfig) {
	node {
		echo "Saveing Patchconfig State ${patchConfig.patchNummer}"
		def patchFileName = "Patch${patchConfig.patchNummer}.json"
		writeFile file: patchFileName , text: new JsonBuilder(patchConfig).toPrettyString()
		def cmd = "/opt/apg-patch-cli/bin/apscli.sh -s ${patchFileName}"
		echo "Executeing ${cmd}"
		sh "${cmd}"
		echo "Executeing ${cmd} done."
	}
}

def stage(target,toState,patchConfig,task, Closure callBack) {
	echo "target: ${target}, toState: ${toState}, task: ${task} "
	patchConfig.targetToState = mapToState(target,toState)
	echo "patchConfig.targetToState: ${patchConfig.targetToState}"
	echo "patchConfig.redoToState: ${patchConfig.redoToState}"
	def skip = patchConfig.redo &&
			(!patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString())
			|| (patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString())
			&& task.equals("Approve")))
	echo "skip = ${skip}"
	def stageText = "${target.envName} (${target.targetName}) ${toState} ${task} "  + (skip ? "(Skipped)" : "")
	stage(stageText) {
		if (!skip) {
			echo "Not skipping"
			callBack(patchConfig)
			if (patchConfig.redo && patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString()) && task.equals("Notification")) {
				patchConfig.redo = false
			}
			savePatchConfigState(patchConfig)
		} else {
			"Echo skipping"
		}
	}
}

def installationPostProcess(patchConfig) {
	if(patchConfig.envName.equals("Produktion")) {
		patchfunctions.mergeDbObjectOnHead(patchConfig, patchConfig.envName)
	}
}

def failIf(parm) {
	def testParameter = env.PATCH_SERVICE_TEST ? env.PATCH_SERVICE_TEST	: ""
	if (testParameter.contentEquals(parm)) {
		error("Forced error termination of pipeline, for testing purposes with parameter: ${parm}")
	}
}

def mavenLocalRepo(patchConfig) {
	node {
		dir('mavenLocalRepo') {
			patchConfig.mavenLocalRepo = pwd()
		}
	}
}

def loadTargetsMap() {
	def configFileLocation = env.PATCH_SERVICE_COMMON_CONFIG ? env.PATCH_SERVICE_COMMON_CONFIG	: "/etc/opt/apg-patch-common/TargetSystemMappings.json"
	def targetSystemFile = new File(configFileLocation)
	assert targetSystemFile.exists()
	def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
	def targetSystemMap = [:]
	jsonSystemTargets.targetSystems.each( { target ->
		targetSystemMap.put(target.name, [envName:target.name,targetName:target.target,typeInd:target.typeInd])
	})
	println targetSystemMap
	targetSystemMap
}

def tagName(patchConfig) {
	patchConfig.patchTag
}

def targetIndicator(patchConfig, target) {
	patchConfig.targetBean = target
	patchConfig.envName = target.envName
	patchConfig.installationTarget = target.targetName
	patchConfig.targetInd = target.typeInd
}

def mavenVersionNumber(patchConfig,revision) {
	return patchConfig.baseVersionNumber + "." + patchConfig.revisionMnemoPart + "-" + revision
}

def getCurrentProdRevision() {
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -pr"
	def revision = sh ( returnStdout : true, script: cmd).trim()
	return revision
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

// TODO (che,16.8): Deprecated, with be removed, keep as fallback
def patchBuilds(patchConfig) {
	node {
		deleteDir()
		lock("${patchConfig.serviceName}${patchConfig.installationTarget}Build") {
			checkoutModules(patchConfig)
			nextRevision(patchConfig)
			generateVersionProperties(patchConfig)
			buildAndReleaseModules(patchConfig)
			saveRevisions(patchConfig)
		}
	}
}

def patchBuildsConcurrent(patchConfig) {
	node {
		deleteDir()
		lock("${patchConfig.serviceName}${patchConfig.installationTarget}Build") {
			coFromBranchCvs(patchConfig, 'it21-ui-bundle', 'microservice')
			nextRevision(patchConfig)
			generateVersionProperties(patchConfig)
			buildAndReleaseModulesConcurrent(patchConfig)
			saveRevisions(patchConfig)
		}
	}
}

def nextRevision(patchConfig) {
	setPatchRevision(patchConfig)
	setPatchLastRevision(patchConfig)
}

def setPatchLastRevision(patchConfig) {
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -lr ${patchConfig.installationTarget}"
	def lastRevision = sh ( returnStdout : true, script: cmd).trim()
	patchConfig.lastRevision = lastRevision
}

def setPatchRevision(patchConfig) {
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -nr"
	def revision = sh ( returnStdout : true, script: cmd).trim()
	patchConfig.revision = revision
}

def saveRevisions(patchConfig) {
	if(isPatchForProdTarget(patchConfig)) {
		saveProdRevision(patchConfig)
	}
	else {
		saveNonProdRevision(patchConfig)
	}
}

def isPatchForProdTarget(def patchConfig) {
	def targetMap = loadTargetsMap()
	return targetMap.get("Produktion").get("targetName").equalsIgnoreCase("${patchConfig.installationTarget}") 	
}

def saveProdRevision(def patchConfig) {
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -spr ${patchConfig.revision}"
	def result = sh returnStatus: true, script: "${cmd}"
	assert result == 0 : println("Error while setting PROD revision to ${patchConfig.revision}")
}

def saveNonProdRevision(def patchConfig) {
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -ar ${patchConfig.installationTarget},${patchConfig.revision}"
	def result = sh returnStatus: true, script: "${cmd}"
	assert result == 0 : println("Error while adding revision ${patchConfig.revision} to target ${patchConfig.installationTarget}")
}


// TODO (che,16.8): Deprecated, with be removed, keep as fallback
def buildAndReleaseModules(patchConfig) {
	patchConfig.mavenArtifactsToBuild.each { buildAndReleaseModule(patchConfig,it) }
}

def buildAndReleaseModulesConcurrent(patchConfig) {
	def artefacts = patchConfig.mavenArtifactsToBuild;
	def listsByDepLevel = artefacts.groupBy { it.dependencyLevel }
	def depLevels = listsByDepLevel.keySet() as List
	depLevels.sort()
	depLevels.reverse(true)
	println depLevels
	depLevels.each { depLevel ->
		def artifactsToBuildParallel = listsByDepLevel[depLevel]
		println artifactsToBuildParallel
		def parallelBuilds = artifactsToBuildParallel.collectEntries {
			[ "Building Level: ${depLevel} and Module: ${it.name}" : buildAndReleaseModulesConcurrent(patchConfig,it)]
		}
		parallel parallelBuilds
	}
}

def buildAndReleaseModulesConcurrent(patchConfig,module) {
	return {
		node {
			def tag = tagName(patchConfig)
			coFromTagcvs(patchConfig,tag,module.name)
			coFromBranchCvs(patchConfig, 'it21-ui-bundle', 'microservice')
			buildAndReleaseModule(patchConfig,module)
		}
	}
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
	patchConfig.mavenArtifactsToBuild.each {
		coFromTagcvs(patchConfig,tag,it.name)
	}
	coFromBranchCvs(patchConfig, 'it21-ui-bundle', 'microservice')
}

def coFromBranchCvs(patchConfig, moduleName, type) {
	def cvsBranch = patchConfig.microServiceBranch
	if(type.equals("db")) {
		cvsBranch = patchConfig.dbPatchBranch
	}
	def callBack = benchmark()
	def duration = callBack {
		checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: cvsBranch, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]], skipChangeLog: false])
	}
	echo "Checkoout of ${moduleName} took ${duration} ms"
}
def coFromTagcvs(patchConfig,tag, moduleName) {
	def callBack = benchmark()
	def duration = callBack {
		checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]], skipChangeLog: false])
	}
	echo "Checkoout of ${moduleName} took ${duration} ms"
}

def generateVersionProperties(patchConfig) {
	def previousVersion = mavenVersionNumber(patchConfig,patchConfig.lastRevision)
	def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
	echo "$buildVersion"
	dir ("it21-ui-bundle") {
		sh "chmod +x ./gradlew"
		sh "./gradlew clean it21-ui-dm-version-manager:publish -PsourceVersion=${previousVersion} -PpublishVersion=${buildVersion} -PpatchFile=file:/${patchConfig.patchFilePath}"
	}
}

def releaseModule(patchConfig,module) {
	dir ("${module.name}") {
		echo "Releasing Module : " + module.name + " for Revision: " + patchConfig.revision + " and: " +  patchConfig.revisionMnemoPart
		def mvnCommand = "mvn -Dmaven.repo.local=${patchConfig.mavenLocalRepo} " + 'clean build-helper:parse-version versions:set -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.\\${parsedVersion.incrementalVersion}.' + patchConfig.revisionMnemoPart + '-' + patchConfig.revision
		echo "${mvnCommand}"
		withMaven( maven: 'apache-maven-3.5.0') { sh "${mvnCommand}" }
	}
}

def buildModule(patchConfig,module) {
	dir ("${module.name}") {
		echo "Building Module : " + module.name + " for Revision: " + patchConfig.revision + " and: " +  patchConfig.revisionMnemoPart
		def mvnCommand = "mvn -Dmaven.repo.local=${patchConfig.mavenLocalRepo} deploy"
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
		sh "./gradlew clean it21-ui-dm-version-manager:publish -PsourceVersion=${buildVersion} -Partifact=${module.groupId}:${module.artifactId} -PpatchFile=file:/${patchConfig.patchFilePath}"
	}
}


def assembleDeploymentArtefacts(patchConfig) {
	node {
		coDbModules(patchConfig)
		dbAssemble(patchConfig)
		coFromBranchCvs(patchConfig, 'it21-ui-bundle', 'microservice')
		assemble(patchConfig, "it21-ui-pkg-server")
		buildDockerImage(patchConfig)
		assemble(patchConfig, "it21-ui-pkg-client")
	}
}

def dbAssemble(patchConfig) {
	def PatchDbFolderName = getCoPatchDbFolderName(patchConfig)
	fileOperations ([folderCreateOperation(folderPath: "${PatchDbFolderName}\\config")])
	// Done in order for the config folder to be taken into account when we create the ZIP...
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\config\\dummy.txt", fileContent: "")])
	def cmPropertiesContent = "config_name:${PatchDbFolderName}\r\npatch_name:${PatchDbFolderName}\r\ntag_name:${PatchDbFolderName}"
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\cm_properties.txt", fileContent: cmPropertiesContent)])
	def configInfoContent = "config_name:${PatchDbFolderName}"
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\config_info.txt", fileContent: configInfoContent)])

	def installPatchContent = "@echo off\r\n"
	// TODO (jhe) :  0900C info doesn't exist at the moment witin patchConfig... also datetime ... do we have it somewhere?
	installPatchContent += "@echo *** Installation von Patch 0900C_${patchConfig.patchNummer} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
	installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
	installPatchContent += "pushd %~dp0 \r\n\r\n"
	installPatchContent += "cmd /c \\\\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
	installPatchContent += "popd"
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\install_patch.bat", fileContent: installPatchContent)])

	publishDbAssemble(patchConfig)
}

def publishDbAssemble(patchConfig) {
	def server = patchDeployment.initiateArtifactoryConnection()
	def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
	def zipName = "${patchDbFolderName}.zip"
	fileOperations ([fileDeleteOperation(includes: zipName)])
	zip zipFile: zipName, glob: "${patchDbFolderName}/**"


	// TODO JHE: Target should better be a subfolder within releases ... like "db"
	def uploadSpec = """{
		"files": [
		{
			"pattern": "*.zip",
			"target": "dbpatch/"
		  }
		]
	}"""
	server.upload(uploadSpec)
}

def getCoPatchDbFolderName(patchConfig) {
	return "${patchConfig.dbPatchBranch.replace('Patch', 'test')}-${patchConfig.revisionMnemoPart}-${patchConfig.revision}"
}

def mergeDbObjectOnHead(patchConfig, envName) {
	/*
	 * JHE (22.05.2018): Within this function, we're calling a "cvs" command from shell. This is not ideal, and at best we should use a similar SCM Command as within
	 * 					 coFromTagcvs method. So far I didn't find an equivalent build-in function allowing to do a merge.
	 * 
	 */

	node {
		echo "Following object will be merge from ${patchConfig.patchTag} to ${patchConfig.prodBranch}:"
		echo "${patchConfig.dbObjectsAsVcsPath}"

		def dbObjects = patchConfig.dbObjectsAsVcsPath
		def folder = ""
		def tag = tagName(patchConfig)
		def cvsRoot = patchConfig.cvsroot

		/*
		 * JHE (22.05.2018): Not sure if we really want to separate the merge and the commit. Idea for separation is obviously that if we encounter an issue during merge, we
		 * 					 don't commit anything. 
		 * TODO JHE: verify the above is actually true? Do we really get an exception?? 
		 * 
		 */

		dbObjects.each{ dbo ->
			//coFromTagcvs(patchConfig,tag,dbo)
			// JHE(22.05.2018): ideally we would like to use the coFromTagCvs method. But we need a .CVS in the checked out folders, which doesn't happen with our coFromTagCvs method.
			sh "cvs -d${cvsRoot} co ${dbo}"
			folder = dbo.substring(0,dbo.lastIndexOf("/"))
			dir(folder) {
				// Switch to head
				sh "cvs -d${cvsRoot} up -A"
				// Merge from tag
				sh "cvs -d${cvsRoot} up -j ${tag}"
				echo "${dbo} has been merged from ${tag} to head"
			}
		}

		dbObjects.each{ dbo ->
			folder = dbo.substring(0,dbo.lastIndexOf("/"))
			dir(folder) {
				// Commit
				sh "cvs -d${cvsRoot} commit -m'Commit of merged ${dbo} from ${tag} tag.'"
				echo "${dbo} has been committed on head after beeing merged from ${tag}."
			}
		}
	}
}

def coDbModules(patchConfig) {
	def dbObjects = patchConfig.dbObjectsAsVcsPath
	echo "Following DB Objects will be checked out : ${dbObjects}"
	def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
	fileOperations ([folderDeleteOperation(folderPath: "${patchDbFolderName}")])
	fileOperations ([folderCreateOperation(folderPath: "${patchDbFolderName}")])
	def tag = tagName(patchConfig)
	dir(patchDbFolderName) {
		dbObjects.each{ dbo ->
			coFromTagcvs(patchConfig,tag, dbo)
		}
	}
}

def buildDockerImage(patchConfig) {
	def extension = patchConfig.dockerBuildExtention
	def artifact = patchConfig.jadasServiceArtifactName
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	patchConfig.runningNr = env.BUILD_NUMBER
	def mvnCommand = "mvn -Dmaven.repo.local=${patchConfig.mavenLocalRepo} org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=${artifact}:${buildVersion}:${extension} -Dtransitive=false"
	echo "${mvnCommand}"
	def mvnCommandCopy = "mvn -Dmaven.repo.local=${patchConfig.mavenLocalRepo} org.apache.maven.plugins:maven-dependency-plugin:2.8:copy -Dartifact=${artifact}:${buildVersion}:${extension} -DoutputDirectory=./distributions"
	echo "${mvnCommandCopy}"

	def dropName = jadasServiceDropName(patchConfig)
	def dockerBuild = "/opt/apgops/docker/build.sh jadas-service ${WORKSPACE}/distributions/${dropName} ${patchConfig.patchNummer}-${patchConfig.revision}-${patchConfig.runningNr}"
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

def redoToState(patchConfig) {
	if (!patchConfig.redo) {
		patchConfig.redoToState = ""
		return
	}
	node {
		echo "Retrieving Redo ToState for ${patchConfig.patchNummer}"
		def cmd = "/opt/apg-patch-cli/bin/apsdbcli.sh -rsta ${patchConfig.patchNummer}"
		echo "Executeing ${cmd}"
		patchConfig.redoToState = sh ( returnStdout : true, script: cmd).trim()
		echo "Redo ToState: ${patchConfig.redoToState}"
		echo "Executeing ${cmd} done."
	}

}


def notify(patchConfig) {
	failIf("fail=${patchConfig.targetToState}")
	node {
		echo "Notifying ${patchConfig.targetToState}"
		def cmd = "/opt/apg-patch-cli/bin/apsdbcli.sh -sta ${patchConfig.patchNummer},${patchConfig.targetToState}"
		echo "Executeing ${cmd}"
		def resultOk = sh ( returnStdout : true, script: cmd).trim()
		echo resultOk
		assert resultOk
		echo "Executeing ${cmd} done"
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