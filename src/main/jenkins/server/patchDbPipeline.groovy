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
		
		node (env.JENKINS_INSTALLER){
			echo "Trying to create folder on cm-linux..."
			
			// TODO JHE:  cm-linux.apgsga.ch needs to be resolved as parameter
			//			  probably dbPatchBranch is not the correct place to take the name from. But for now, patchConfig doesn't contain 0900C1 alone...
			def newFolderName = patchConfig.dbPatchBranch.replace("Patch", "test")
			fileOperations ([folderCreateOperation(folderPath: "\\\\cm-linux.apgsga.ch\\cm_patch_download\\${newFolderName}")])
		}
		
	}
}
stage("${target.targetName} Installation") {
	
	// should occur on a node, stash and unstash
	echo "Installing DB Object ... TODO ..."
}