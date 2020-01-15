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

def nodeLabelFor(serviceName, nodes) {
	def found = nodes.find { it.serviceName.equals(serviceName)}
	if (found != null) {
		found.label
	} else {
		found
	}
	
}

def readPatchFile(patchFilePath) {
	def patchFile = new File(patchFilePath)
	def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
	patchConfig.patchFilePath = patchFilePath
	patchConfig
}

def initPatchConfig(patchConfig, params) {
	patchConfig.cvsroot = env.CVS_ROOT
	patchConfig.patchFilePath = params.PARAMETER
	patchConfig.redo = params.RESTART.equals("TRUE")
}

def savePatchConfigState(patchConfig) {
	node {
		log("Saving Patchconfig State ${patchConfig.patchNummer}","savePatchConfigState")
		def patchFileName = "Patch${patchConfig.patchNummer}.json"
		writeFile file: patchFileName , text: new JsonBuilder(patchConfig).toPrettyString()
		def cmd = "/opt/apg-patch-cli/bin/apscli.sh -s ${patchFileName}"
		log("Executeing ${cmd}","savePatchConfigState")
		sh "${cmd}"
		log("Executeing ${cmd} done.","savePatchConfigState")
	}
}

def serviceInstallationNodeLabel(target,serviceName) {
	target.nodes.each{node -> 
		if(node.serviceName.equalsIgnoreCase(serviceName)) {
			label = node.label
		}
	}
	assert label?.trim() : "No label found for ${serviceName}"
	return label
}

def stage(target,toState,patchConfig,task, Closure callBack) {
	log("target: ${target}, toState: ${toState}, task: ${task} ","stage")
	patchConfig.currentPipelineTask = "${task}"
	logPatch(patchConfig,"started")
	def targetSystemsMap = loadTargetsMap()
	def targetName= targetSystemsMap.get(target.envName)
	patchConfig.targetToState = mapToState(target,toState)
	log("patchConfig.targetToState: ${patchConfig.targetToState}","stage")
	log("patchConfig.redoToState: ${patchConfig.redoToState}","stage")
	def skip = patchConfig.redo &&
			(!(patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString()) && patchConfig.lastPipelineTask.toString().equals(task.toString())))
	def nop = !skip && patchConfig.mavenArtifacts.empty && patchConfig.dbObjects.empty && !patchConfig.installJadasAndGui && !patchConfig.installDockerAndWindowsServices && !["Approve","Notification"].contains(task)
	log("skip = ${skip}","stage")
	log("nop  = ${nop}","stage")
	def stageText = "${target.envName} (${target.targetName}) ${toState} ${task} "  + (skip ? "(Skipped)" : (nop ? "(Nop)" : "") )
	def logText
	stage(stageText) {
		if (!skip) {
			log("Not skipping","stage")
			// Save before Stage 
			if (targetName != null) {
				savePatchConfigState(patchConfig)
			}
			if (!nop) {
				callBack(patchConfig)
				patchConfig.lastPipelineTask = task
			}
			if (patchConfig.redoToState.toString().equals(patchConfig.targetToState.toString()) && patchConfig.lastPipelineTask.toString().equals(task.toString())) {
				patchConfig.redo = false
			}
			if (targetName != null) {
				savePatchConfigState(patchConfig)
			}
			// JHE: instead of "nop", what could we better write for the developer?
			logText =  nop ? "nop" : "done"
		} else {
			log("skipping","stage")
			logText = "skipped"
		}
	}
	logPatch(patchConfig, logText)
}

private def logPatch(def patchConfig, def logText) {
	node {
		patchConfig.logText = logText
		def patchFileName = "PatchLog${patchConfig.patchNummer}.json" 
		writeFile file: patchFileName , text: new JsonBuilder(patchConfig).toPrettyString()
		def cmd = "/opt/apg-patch-cli/bin/apscli.sh -log ${patchFileName}"
		log("Executeing ${cmd}","logPatch")
		sh "${cmd}"
		log("Executeing ${cmd} done.","logPatch")
	}
}

def installationPostProcess(patchConfig) {
	if(patchConfig.envName.equals("Produktion")) {
		mergeDbObjectOnHead(patchConfig, patchConfig.envName)
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
	def targetSystemMap = [:]
	getTargetSystemMappingJson().stageMappings.each( { target ->
		targetSystemMap.put(target.name, [envName:target.name,targetName:target.target])
	})
	log(targetSystemMap,"loadTargetsMap")
	targetSystemMap
}

def getTargetSystemMappingJson() {
	def configFileLocation = env.PATCH_SERVICE_COMMON_CONFIG ? env.PATCH_SERVICE_COMMON_CONFIG	: "/etc/opt/apg-patch-common/TargetSystemMappings.json"
	def targetSystemFile = new File(configFileLocation)
	assert targetSystemFile.exists()
	return new JsonSlurper().parseText(targetSystemFile.text)
}

def getTargetInstance(targetInstanceName,targetSystemMappingJson) {
	log("Fetching targetInstance called ${targetInstanceName} from following JSON: ${targetSystemMappingJson}","getTargetInstance")
	def res = targetSystemMappingJson.targetInstances.find{it.name == targetInstanceName}
	return res
}

def tagName(patchConfig) {
	if (patchConfig.patchTag?.trim()) {
		patchConfig.patchTag
	} else {
		patchConfig.developerBranch
	}
		
}

def saveTarget(patchConfig, target) {
	patchConfig.targetBean = target
	patchConfig.envName = target.envName
	patchConfig.currentTarget = target.targetName
}

def mavenVersionNumber(patchConfig,revision) {
	return patchConfig.baseVersionNumber + "." + patchConfig.revisionMnemoPart + "-" + revision
}


def approveBuild(patchConfig) {
	userInput = input (id:"Patch${patchConfig.patchNummer}BuildFor${patchConfig.currentTarget}Ok" , message:"Ok for ${patchConfig.currentTarget} Build?" , submitter: 'svcjenkinsclient')
}

def approveInstallation(patchConfig) {
	userInput = input (id:"Patch${patchConfig.patchNummer}InstallFor${patchConfig.currentTarget}Ok" , message:"Ok for ${patchConfig.currentTarget} Installation?" , submitter: 'svcjenkinsclient')
}


def patchBuildsConcurrent(patchConfig) {
	node {
		deleteDir()
		lock("${patchConfig.serviceName}${patchConfig.currentTarget}Build") {
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
	def fullRevPrefix = getFullRevisionPrefix(patchConfig)
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -lr ${patchConfig.currentTarget}"
	def lastTargetRevision = sh ( returnStdout : true, script: cmd).trim()
	patchConfig.lastRevision = "${fullRevPrefix}${lastTargetRevision}"
	log("patchConfig.lastRevision has been set with last Revision for target ${patchConfig.currentTarget}: ${lastTargetRevision}","setPatchLastRevision")
}

def setPatchRevision(patchConfig) {
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -nr"
	def revision = sh ( returnStdout : true, script: cmd).trim()
	patchConfig.revision = revision
	log("patchConfig.revision has been set with ${revision}","setPatchRevision")
}

def saveRevisions(patchConfig) {
	def fullRevPrefix = getFullRevisionPrefix(patchConfig)
	def cmd = "/opt/apg-patch-cli/bin/apsrevcli.sh -ar ${patchConfig.currentTarget},${patchConfig.revision},${fullRevPrefix}"
	def result = sh returnStatus: true, script: "${cmd}"
	assert result == 0 : log("Error while adding revision ${patchConfig.revision} to target ${patchConfig.currentTarget}","saveRevisions")
	log("New Revision has been added for ${patchConfig.currentTarget}: ${fullRevPrefix}","saveRevisions")
}

def getFullRevisionPrefix(def patchConfig) {
	return "${patchConfig.baseVersionNumber}.${patchConfig.revisionMnemoPart}-"
}

def buildAndReleaseModulesConcurrent(patchConfig) {
	def artefacts = patchConfig.mavenArtifactsToBuild;
	def listsByDepLevel = artefacts.groupBy { it.dependencyLevel }
	def depLevels = listsByDepLevel.keySet() as List
	depLevels.sort()
	depLevels.reverse(true)
	log(depLevels,"buildAndReleaseModulesConcurrent")
	depLevels.each { depLevel ->
		def artifactsToBuildParallel = listsByDepLevel[depLevel]
		log(artifactsToBuildParallel,"buildAndReleaseModulesConcurrent")
		def parallelBuilds = artifactsToBuildParallel.collectEntries {
			[ "Building Level: ${it.dependencyLevel} and Module: ${it.name}" : buildAndReleaseModulesConcurrent(patchConfig,it)]
		}
		parallel parallelBuilds
	}
}

def buildAndReleaseModulesConcurrent(patchConfig,module) {
	return {
		node {
			def tag = tagName(patchConfig)
			coFromTagCvsConcurrent(patchConfig,tag,module.name)
			coIt21BundleFromBranchCvs(patchConfig) 
			buildAndReleaseModule(patchConfig,module)
		}
	}
}

// TODO (che, 29.10) not very efficient
def coFromTagCvsConcurrent(patchConfig,tag,module) {
	lock ("ConcurrentCvsCheckout") {
		coFromTagcvs(patchConfig, tag, module)
	}
}

// TODO (che, 29.10) not very efficient
def coIt21BundleFromBranchCvs(patchConfig) {
	lock ("ConcurrentCvsCheckout") {
		coFromBranchCvs(patchConfig, 'it21-ui-bundle', 'microservice')
	}
}

def buildAndReleaseModule(patchConfig,module) {
	log("buildAndReleaseModule : " + module.name,"buildAndReleaseModule")
	releaseModule(patchConfig,module)
	buildModule(patchConfig,module)
	updateBom(patchConfig,module)
	log("buildAndReleaseModule : " + module.name,"buildAndReleaseModule")

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
		checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
				[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
						[location: [$class: 'BranchRepositoryLocation', branchName: cvsBranch, useHeadIfNotFound: false],  modules: [
								[localName: moduleName, remoteName: moduleName]
							]]
					]]
			], skipChangeLog: false])
	}
	log("Checkout of ${moduleName} took ${duration} ms","coFromBranchCvs")
}
def coFromTagcvs(patchConfig,tag, moduleName) {
	def callBack = benchmark()
	def duration = callBack {
		checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
				[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
						[location: [$class: 'TagRepositoryLocation', tagName: tag, useHeadIfNotFound: false],  modules: [
								[localName: moduleName, remoteName: moduleName]
							]]
					]]
			], skipChangeLog: false])
	}
	log("Checkout of ${moduleName} took ${duration} ms","coFromTagcvs")
}

def generateVersionProperties(patchConfig) {

	def previousVersion = patchConfig.lastRevision.equals("SNAPSHOT") ? "${patchConfig.baseVersionNumber}.${patchConfig.revisionMnemoPart}-${patchConfig.lastRevision}" : patchConfig.lastRevision
	def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
	log("$buildVersion","generateVersionProperties")
	dir ("it21-ui-bundle") {
		log("Publishing new Bom from previous Version: " + previousVersion  + " to current Revision: " + buildVersion,"generateVersionProperties")
		sh "chmod +x ./gradlew"
		sh "./gradlew clean it21-ui-dm-version-manager:publish it21-ui-dm-version-manager:publishToMavenLocal -PsourceVersion=${previousVersion} -PpublishVersion=${buildVersion} -PpatchFile=file:/${patchConfig.patchFilePath}"
	}
}

def releaseModule(patchConfig,module) {
	dir ("${module.name}") {
		log("Releasing Module : " + module.name + " for Revision: " + patchConfig.revision + " and: " +  patchConfig.revisionMnemoPart,"releaseModule")
		def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
		def mvnCommand = "mvn -DbomVersion=${buildVersion}" + ' clean build-helper:parse-version versions:set -DnewVersion=\\${parsedVersion.majorVersion}.\\${parsedVersion.minorVersion}.\\${parsedVersion.incrementalVersion}.' + patchConfig.revisionMnemoPart + '-' + patchConfig.revision
		log("${mvnCommand}","releaseModule")
		withMaven( maven: 'apache-maven-3.5.0') { sh "${mvnCommand}" }
	}
}

def buildModule(patchConfig,module) {
	dir ("${module.name}") {
		def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
		log("Building Module : " + module.name + " for Version: " + buildVersion,"buildModule")
		def mvnCommand = "mvn -DbomVersion=${buildVersion} clean deploy"
		log("${mvnCommand}","buildModule")
		lock ("BomUpdate${buildVersion}") {
			withMaven( maven: 'apache-maven-3.5.0') { sh "${mvnCommand}" }
		}		
	}
}

def updateBom(patchConfig,module) {
	log("Update Bom for artifact " + module.artifactId + " for Revision: " + patchConfig.revision,"updateBom")
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	log("Bom source version which will be update: ${buildVersion}","updateBom")
	lock ("BomUpdate${buildVersion}") {
		dir ("it21-ui-bundle") {
			sh "chmod +x ./gradlew"
			sh "./gradlew clean it21-ui-dm-version-manager:publish it21-ui-dm-version-manager:publishToMavenLocal -PsourceVersion=${buildVersion} -Partifact=${module.groupId}:${module.artifactId} -PpatchFile=file:/${patchConfig.patchFilePath}"
		}
	}
}

def assembleDeploymentArtefacts(patchConfig) {
	node {
		coDbModules(patchConfig)
		dbAssemble(patchConfig)
		coFromBranchCvs(patchConfig, 'it21-ui-bundle', 'microservice')
		assemble(patchConfig)
	}
}

def dbAssemble(patchConfig) {
	def PatchDbFolderName = getCoPatchDbFolderName(patchConfig)
	fileOperations ([
		folderCreateOperation(folderPath: "${PatchDbFolderName}\\config")
	])
	// Done in order for the config folder to be taken into account when we create the ZIP...
	fileOperations ([
		fileCreateOperation(fileName: "${PatchDbFolderName}\\config\\dummy.txt", fileContent: "")
	])
	def cmPropertiesContent = "config_name:${PatchDbFolderName}\r\npatch_name:${PatchDbFolderName}\r\ntag_name:${PatchDbFolderName}"
	fileOperations ([
		fileCreateOperation(fileName: "${PatchDbFolderName}\\cm_properties.txt", fileContent: cmPropertiesContent)
	])
	def configInfoContent = "config_name:${PatchDbFolderName}"
	fileOperations ([
		fileCreateOperation(fileName: "${PatchDbFolderName}\\config_info.txt", fileContent: configInfoContent)
	])

	def installPatchContent = "@echo off\r\n"
	// TODO (jhe) :  0900C info doesn't exist at the moment witin patchConfig... also datetime ... do we have it somewhere?
	installPatchContent += "@echo *** Installation von Patch 0900C_${patchConfig.patchNummer} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
	installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
	installPatchContent += "pushd %~dp0 \r\n\r\n"
	installPatchContent += "cmd /c \\\\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
	installPatchContent += "popd"
	fileOperations ([
		fileCreateOperation(fileName: "${PatchDbFolderName}\\install_patch.bat", fileContent: installPatchContent)
	])

	publishDbAssemble(patchConfig)
}

def publishDbAssemble(patchConfig) {
	def server = patchDeployment.initiateArtifactoryConnection()
	def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
	def zipName = "${patchDbFolderName}.zip"
	fileOperations ([
		fileDeleteOperation(includes: zipName)
	])
	zip zipFile: zipName, glob: "${patchDbFolderName}/**"


	// TODO JHE: Target should better be a subfolder within releases ... like "db"
	def uploadSpec = """{
		"files": [
		{
			"pattern": "*.zip",
			"target": "${env.DB_PATCH_REPO}"
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
		def cvsRoot = patchConfig.cvsroot
		
		def patchNumber = patchConfig.patchNummer
		def dbPatchTag = patchConfig.patchTag
		def dbProdBranch = patchConfig.prodBranch
		def dbPatchBranch = patchConfig.dbPatchBranch
		
		def dbTagBeforeMerge = "${dbProdBranch}_merge_${dbPatchBranch}_before"
		def dbTagAfterMerge = "${dbProdBranch}_merge_${dbPatchBranch}_after"

		log("Patch \"${patchNumber}\" being merged to production branch","mergeDbObjectOnHead")
		patchConfig.dbObjects.collect{it.moduleName}.unique().each { dbModule ->
			log("- module \"${dbModule}\" tag \"${dbPatchTag}\" being merged to branch \"${dbProdBranch}\"","mergeDbObjectOnHead")
			sh "cvs -d${cvsRoot} co -r${dbProdBranch} ${dbModule}"
			log("... ${dbModule} checked out from branch \"${dbProdBranch}\"","mergeDbObjectOnHead")
			sh "cvs -d${cvsRoot} tag -F ${dbTagBeforeMerge} ${dbModule}"
			log("... ${dbModule} tagged ${dbTagBeforeMerge}","mergeDbObjectOnHead")
			sh "cvs -d${cvsRoot} up -j ${dbPatchTag} ${dbModule}"
			log("... ${dbModule} tag \"${dbPatchTag}\" merged to branch \"${dbProdBranch}\"","mergeDbObjectOnHead")
			sh "cvs -d${cvsRoot} commit -m 'merge ${dbPatchTag} to branch ${dbProdBranch}' ${dbModule}"
			log("... ${dbModule} commited","mergeDbObjectOnHead")
		    sh "cvs -d${cvsRoot} tag -F ${dbTagAfterMerge} ${dbModule}"
			log("... ${dbModule} tagged ${dbTagAfterMerge}","mergeDbObjectOnHead")
			log("- module \"${dbModule}\" tag \"${dbPatchTag}\" merged to branch \"${dbProdBranch}\"","mergeDbObjectOnHead")
		}
		log("Patch \"${patchNumber}\" merged to production branch","mergeDbObjectOnHead")
	}
}

def coDbModules(patchConfig) {
	def dbObjects = patchConfig.dbObjectsAsVcsPath
	log("Following DB Objects should get checked out : ${dbObjects}","coDbModules")
	
	def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
	fileOperations ([
		folderDeleteOperation(folderPath: "${patchDbFolderName}")
	])
	fileOperations ([
		folderCreateOperation(folderPath: "${patchDbFolderName}")
	])
	/*
	** work-around for not yet existing packaging of db scripts, see ticket CM-216
	*/
	fileOperations ([
		folderCreateOperation(folderPath: "${patchDbFolderName}/oracle")
	])

	def cvsRoot = patchConfig.cvsroot
	
	def patchNumber = patchConfig.patchNummer
	def dbPatchTag = patchConfig.patchTag
	
	log("Patch \"${patchNumber}\" being checked out to \"${patchDbFolderName}/oracle\"","coDbModule")
	patchConfig.dbObjects.collect{it.moduleName}.unique().each { dbModule ->
		log("- module \"${dbModule}\" tag \"${dbPatchTag}\" being checked out","coDbModule")
		dir("${patchDbFolderName}/oracle") {
			def moduleDirectory = dbModule.replace(".","_")
			sh "cvs -d${cvsRoot} co -r${dbPatchTag} -d${moduleDirectory} ${dbModule}"
		}
	}
	log("Patch \"${patchNumber}\" checked out","coDbModule")

}

def jadasVersionNumber(patchConfig) {
	return "${patchConfig.revision}.${patchConfig.patchNummer}"
}

def assemble(patchConfig) {
	def buildVersion = mavenVersionNumber(patchConfig,patchConfig.revision)
	def jadasPublishVersion = jadasVersionNumber(patchConfig)
	log("Building Assembly with version: ${buildVersion} ","assemble")
	dir ("it21-ui-bundle") {
		sh "chmod +x ./gradlew"
		// Assemble and publish GUI
		sh "./gradlew it21-ui-pkg-client:assemble it21-ui-pkg-client:publish -PsourceVersion=${buildVersion}"
		// Assemble and publish Jadas
		sh "./gradlew it21-ui-pkg-server:assemble it21-ui-pkg-server:publish -PsourceVersion=${buildVersion} -PpublishVersion=${jadasPublishVersion} -PbuildTarget=${patchConfig.currentTarget}"
	}
}

def redoToState(patchConfig) {
	if (!patchConfig.redo) {
		patchConfig.redoToState = ""
		return
	}
	patchConfig.redoToState = patchConfig.targetToState
}


def notify(patchConfig) {
	failIf("fail=${patchConfig.targetToState}")
	node {
		log("Notifying ${patchConfig.targetToState}","notify")
		def cmd = "/opt/apg-patch-cli/bin/apsdbcli.sh -sta ${patchConfig.patchNummer},${patchConfig.targetToState}"
		log("Executeing ${cmd}","notify")
		def resultOk = sh ( returnStdout : true, script: cmd).trim()
		log(resultOk,"notify")
		assert resultOk
		log("Executeing ${cmd} done","notify")
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