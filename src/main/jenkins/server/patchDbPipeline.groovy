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
	stage("${target.targetName} Build" ) {
		def node = node {
			
			echo "Building object for DB, for now basically a checkout of ${patchConfig.dbPatchBranch} CVS branch"
			// get all what's in dbObjectsAsVcsPath
		
			def dbObjects = patchConfig.dbObjectsAsVcsPath
			echo "Following DB Objects will be checked out : ${dbObjects}"
			
			dbObjects.each{ dbo ->
				echo "Checking out = ${dbo}"	
				checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.dbPatchBranch, useHeadIfNotFound: false],  modules: [[localName: dbo, remoteName: dbo]]]]]], skipChangeLog: false])
			}
		}
	}
	stage("${target.targetName} Assembly" ) {
		
		// Stashing all SQL from workspace
		node {
			stash name: "sqls", includes: "**/*.sql"
		}
		
		node (env.JENKINS_INSTALLER){
			echo "Trying to create folder on cm-linux..."
			
			// TODO JHE:  cm-linux.apgsga.ch needs to be resolved as parameter
			//			  probably dbPatchBranch is not the correct place to take the name from. But for now, patchConfig doesn't contain 0900C1 alone...
			def newFolderName = patchConfig.dbPatchBranch.replace("Patch", "test")
			fileOperations ([folderCreateOperation(folderPath: "\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}")])
			// config folder has to be empty
			fileOperations ([folderCreateOperation(folderPath: "\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}\\config")])
			def cmPropertiesContent = "config_name:${newFolderName}\r\npatch_name:${newFolderName}\r\ntag_name:${newFolderName}"
			fileOperations ([fileCreateOperation(fileName: "\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}\\cm_properties.txt", fileContent: cmPropertiesContent)])
			def configInfoContent = "config_name:${newFolderName}"
			fileOperations ([fileCreateOperation(fileName: "\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}\\config_info.txt", fileContent: configInfoContent)])
			
			def installPatchContent = "@echo off\r\n"
			// TODO JHE: 0900C info doesn't exist at the moment witin patchConfig... also datetime ... do we have it somewhere?
			installPatchContent += "@echo *** Installation von Patch 0900C_${patchConfig.patchNummer} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
			installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
			installPatchContent += "pushd %~dp0 \r\n\r\n"
			installPatchContent += "cmd /c \\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
			installPatchContent += "popd"
			//TODO JHE: This file should be store on Git ?!? Or somewhere else? Or ok to generate all its content dynamically ?!?!
			fileOperations ([fileCreateOperation(fileName: "\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}\\install_patch.bat", fileContent: installPatchContent)])
			
			// Unstashing into patch folder
			dir("\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}") {
				unstash "sqls"
			}
		}

	}
}
stage("${target.targetName} Installation") {
	
	// should occur on a node, stash and unstash
	echo "Installing DB Object ... TODO ..."
}