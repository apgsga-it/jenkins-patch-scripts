#!groovy
// TODO JHE: Not sure that we'll really need these libs ... maybe a new one ?
library 'patch-global-functions'
library 'patch-deployment-functions'
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
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"

// Load Target System Mappings
def targetSystemsMap = patchfunctions.loadTargetsMap()
println "TargetSystemsMap : ${targetSystemsMap} " 

// Mainline
// While mit Start der Pipeline bereits getagt ist

def target = targetSystemsMap.get('Entwicklung')
stage("${target.envName} (${target.targetName}) Installationsbereit Notification") {
	patchfunctions.notify(target,"Installationsbereit", patchConfig)
}

//Main line
patchfunctions.targetIndicator(patchConfig,target)
stage("${target} Build & Assembly") {
	stage("${target} Build" ) {
		// checkout what's on patchConfig.dbPatchBranch : checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.microServiceBranch, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]], skipChangeLog: false])
		
		echo "Building object for DB, for now basically a checkout of ${patchConfig.dbPatchBranch} CVS branch"
		// get all what's in dbObjectsAsVcsPath
		
		def dbObjects = patchConfig.dbObjectsAsVcsPath
		echo "Following DB Objects will be checked out : ${dbObjects}"
		
		dbObjects.each{ dbo ->
			echo "Checking out = ${dbo}"	
			checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.dbPatchBranch, useHeadIfNotFound: false],  modules: [[localName: dbo, remoteName: dbo]]]]]], skipChangeLog: false])
		}
		
	//	checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [[compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [[location: [$class: 'BranchRepositoryLocation', branchName: patchConfig.dbPatchBranch, useHeadIfNotFound: false],  modules: [[localName: moduleName, remoteName: moduleName]]]]]], skipChangeLog: false])
	}
	stage("${target} Assembly" ) {
		
		// ZIP what has been checked out
		// publish it on Artifactory
		
		echo "Assembly object for DB ... TODO ..."
	}
}
stage("${target} Installation") {
	
	// should occur on a node, stash and unstash
	echo "Installing DB Object ... TODO ..."
}