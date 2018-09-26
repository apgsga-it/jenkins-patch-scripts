#!groovy
library 'patch-global-functions'
library 'patch-deployment-functions'
import groovy.json.JsonSlurperClassic
properties([
	parameters([
		stringParam(
		defaultValue: "",
		description: 'Path to Patch*.json File',
		name: 'PARAMETER'
		),
		stringParam(
		defaultValue: "FALSE",
		description: 'Indicator, if the Pipeline should be restartet to the last successful state',
		name: 'RESTART'
		)
	])
])

// Parameter
// TODO  (che, 9.7) When JENKINS-27413 is resolved
// Passing Patch File Path , because of JAVA8MIG-395 / JENKINS-27413
def patchFile = new File(params.PARAMETER)
def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
patchConfig.cvsroot = env.CVS_ROOT
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"
patchConfig.patchFilePath = params.PARAMETER
patchConfig.redo = params.RESTART.equals("TRUE")

// Load Target System Mappings
def targetSystemsMap = patchfunctions.loadTargetsMap()
println "TargetSystemsMap : ${targetSystemsMap} "
// Create a local Maven Repo for Pipeline
patchfunctions.mavenLocalRepo(patchConfig)
println patchConfig.mavenLocalRepo
// Retrieve event. PrecessorsState , which will be skipped if in Restart Modues
patchfunctions.redoToState(patchConfig)

// Mainline
// While mit Start der Pipeline bereits getagt ist

def target = targetSystemsMap.get('Entwicklung')
def skip =!patchConfig.restart || !patchConfig.redoToState.equals(patchfunctions.mapToState(target,"Installationsbereit"))
stage("${target.envName} (${target.targetName}) Installationsbereit Notification "  + (skip ? "(Skipped" : "")) {
	if (!skip) {
		patchfunctions.notify(target,"Installationsbereit", patchConfig)
	}
}

['Informatiktest', 'Produktion'].each { envName ->
	target = targetSystemsMap.get(envName)
	assert target != null
	patchfunctions.targetIndicator(patchConfig,target)
	skip = !patchConfig.restart || !patchConfig.redoToState.equals(patchfunctions.mapToState(target,"Installationsbereit"))
	stage("Approve ${envName} (${target.targetName}) Build & Assembly ${patchConfig.skipText}") {
		if (!skip) {
			patchfunctions.approveBuild(patchConfig)
		}
	}
	stage("${envName} (${target.targetName}) Build ${patchConfig.skipText}" ) {
		if (!skip) {
			patchfunctions.patchBuildsConcurrent(patchConfig)
		}
	}
	stage("${envName} (${target.targetName}) Assembly ${patchConfig.skipText}" ) {
		if (!skip) {
			patchfunctions.assembleDeploymentArtefacts(patchConfig)
		}
	}
	stage("${envName} (${target.targetName}) Installationsbereit Notification ${patchConfig.skipText}") {
		if (!skip) {
			patchfunctions.notify(target,"Installationsbereit", patchConfig)
		}
	}
	stage("Approve ${envName} (${target.targetName}) Installation ${patchConfig.skipText}") {
		if (!skip) {
			patchfunctions.approveInstallation(patchConfig)
		}
	}
	skip = !patchConfig.restart || !patchConfig.redoToState.equals(patchfunctions.mapToState(target,"Installation"))
	stage("${envName} (${target.targetName}) Installation ${patchConfig.skipText}") {
		if (!skip) {
			patchDeployment.installDeploymentArtifacts(patchConfig)
		}
	}
	stage("${envName} (${target.targetName}) Installation Notification ${patchConfig.skipText}") {
		if(envName.equals("Produktion")) {
			patchfunctions.mergeDbObjectOnHead(patchConfig, envName)
		}
		if (!skip) {
			patchfunctions.notify(target,"Installation", patchConfig)
		}
	}
}