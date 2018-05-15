#!groovy
// TODO JHE: Not sure that we'll really need these libs ... maybe a new one ?
// Yep , i would , global functions are probably needed, at least if you deploy the db scripts artifactory
library 'patch-global-functions'

import groovy.json.JsonSlurperClassic
properties([
	parameters([
		stringParam(
		defaultValue: "",
		description: 'Parameter',
		name: 'PARAMETER'
		)
	])
])

// Parameter

// Parameter
def patchConfig = new JsonSlurperClassic().parseText(params.PARAMETER)
echo patchConfig.toString()
patchConfig.cvsroot = "/var/local/cvs/root"
// Mainline
def target = [envName:"Download",targetName:patchConfig.installationTarget,typeInd:"T"]
patchfunctions.targetIndicator(patchConfig,target)
// TODO( che, 4.5) , why seperate build and assembly for db objects?
// Is'nt check-out and install?
// At least for the Integration Prototype
stage("${target.targetName} Build & Assembly") {
	
	def server = Artifactory.server env.ARTIFACTORY_SERVER_ID
	
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactoryDev',
				usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
	
			server.username = "${USERNAME}"
			server.password = "${PASSWORD}"
		}
	
	
	// TODO JHE:  cm-linux.apgsga.ch needs to be resolved as parameter
	//			  probably dbPatchBranch is not the correct place to take the name from. But for now, patchConfig doesn't contain 0900C1 alone...
	def rootDbFolderName = patchConfig.dbPatchBranch.replace("Patch", "test")
	
	
	node (env.JENKINS_INSTALLER){
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'svcit21install',
			usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
	
				// Mount the share drive
				powershell("net use \\\\cm-linux.apgsga.ch ${PASSWORD} /USER:${USERNAME}")
		}
	}
		
	stage("${target.targetName} Build" ) {
		def node = node {
			
			echo "Building object for DB, for now basically a checkout of ${patchConfig.dbPatchBranch} CVS branch"
			// get all what's in dbObjectsAsVcsPath
		
			def dbObjects = patchConfig.dbObjectsAsVcsPath
			echo "Following DB Objects will be checked out : ${dbObjects}"
			
			
			// First clean any existing db folder... Needed??, and do we want this??
			fileOperations ([folderDeleteOperation(folderPath: "${rootDbFolderName}")])
			fileOperations ([folderCreateOperation(folderPath: "${rootDbFolderName}")])
			
			// Do the checkout direction in the folder which will then be zipped
			dir(rootDbFolderName) {
				dbObjects.each{ dbo ->
					echo "Checking out = ${dbo}"	
					checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.dbPatchBranch, useHeadIfNotFound: false],  modules: [[localName: dbo, remoteName: dbo]]]]]], skipChangeLog: false])
				}
			}
			
			// config folder has to be empty
			fileOperations ([folderCreateOperation(folderPath: "${rootDbFolderName}\\config")])
			// Done in order for the config folder to be taken into account when we create the ZIP...
			fileOperations ([fileCreateOperation(fileName: "${rootDbFolderName}\\config\\dummy.txt", fileContent: "")])
			def cmPropertiesContent = "config_name:${rootDbFolderName}\r\npatch_name:${rootDbFolderName}\r\ntag_name:${rootDbFolderName}"
			fileOperations ([fileCreateOperation(fileName: "${rootDbFolderName}\\cm_properties.txt", fileContent: cmPropertiesContent)])
			def configInfoContent = "config_name:${rootDbFolderName}"
			fileOperations ([fileCreateOperation(fileName: "${rootDbFolderName}\\config_info.txt", fileContent: configInfoContent)])
			
			def installPatchContent = "@echo off\r\n"
			// TODO JHE: 0900C info doesn't exist at the moment witin patchConfig... also datetime ... do we have it somewhere?
			installPatchContent += "@echo *** Installation von Patch 0900C_${patchConfig.patchNummer} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
			installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
			installPatchContent += "pushd %~dp0 \r\n\r\n"
			installPatchContent += "cmd /c \\\\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
			installPatchContent += "popd"
			//TODO JHE: This file should be store on Git ?!? Or somewhere else? Or ok to generate all its content dynamically ?!?!
			fileOperations ([fileCreateOperation(fileName: "${rootDbFolderName}\\install_patch.bat", fileContent: installPatchContent)])
			
		}
	}
	
	stage("${target.targetName} Assembly" ) {
		
		// TODO JHE: Here we actually only want to ZIP and deploy on Artifactoy (or on cm-linux)
		node {
			//TODO JHE: decide what the ZIP name should be ... for now it's the same as within \\cm-linux.apgsga.ch\cm_build_repo
			def zipName = "${rootDbFolderName}.zip"
			fileOperations ([fileDeleteOperation(includes: zipName)])
			zip zipFile: zipName, glob: "${rootDbFolderName}/**"
			
				
			// TODO JHE: Target should better be a subfolder within releases ... like "db"
			def uploadSpec = """{
				"files": [
				{
					"pattern": "*.zip",
					"target": "releases/"
			  	}
				]
			}"""
			server.upload(uploadSpec)
			
		}
		
		
		// Stashing all SQL from workspace
//		node {
//			stash name: "sqls", includes: "**/*.sql"
//		}
		
		/*
		node (env.JENKINS_INSTALLER){
			
			
			echo "Trying to create folder on cm-linux..."
			

			
			// Unstashing into patch folder
			dir("\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}") {
				unstash "sqls"
			}
			
			//Now we can execute command to instll the DB objects
			
		}
		*/

	}
}
stage("${target.targetName} Installation") {
	
	def server = Artifactory.server env.ARTIFACTORY_SERVER_ID
	
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactoryDev',
				usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
	
			server.username = "${USERNAME}"
			server.password = "${PASSWORD}"
		}
	
	
	// TODO JHE:  cm-linux.apgsga.ch needs to be resolved as parameter
	//			  probably dbPatchBranch is not the correct place to take the name from. But for now, patchConfig doesn't contain 0900C1 alone...
	def rootDbFolderName = patchConfig.dbPatchBranch.replace("Patch", "test")
	
	node (env.JENKINS_INSTALLER){
		
		
		
		def cmDownloadPath = "\\\\cm-linux.apgsga.ch\\cm_patch_download"
		
		//fileOperations ([folderCreateOperation(folderPath: "${extractedFolderName}")])
		
		def downloadSpec = """{
              "files": [
                    {
                     "pattern": "releases/*${rootDbFolderName}.zip",
					 "target": "download/"
					 }
		   		]
			}"""
		server.download(downloadSpec)
		
		
		unzip zipFile: "download/${rootDbFolderName}.zip"
		fileOperations ([folderCopyOperation(sourceFolderPath: rootDbFolderName, destinationFolderPath: "${cmDownloadPath}\\${rootDbFolderName}")])
		/*
		fileOperations ([fileCopyOperation(flattenFiles: true, excludes: '', includes: "${extractedFolderName}\\${rootDbFolderName}\\**\\*", targetLocation: extractedFolderName)])
		fileOperations ([fileDeleteOperation(includes: "${extractedFolderName}\\${rootDbFolderName}.zip")])
		fileOperations ([folderDeleteOperation(folderPath: "${extractedFolderName}\\${rootDbFolderName}")])
		*/
		
		
		// TODO JHE: Replace CHEI212 with target ${target.targetName}
		echo "Forcing simulation on CHEI212, normally it would have been on chei212"
		bat("cmd /c \\\\cm-linux.apgsga.ch\\cm_winproc_root\\it21_extensions\\jenkins_pipeline_patch_install.bat ${cmDownloadPath}\\${rootDbFolderName} chei212")
	}
}