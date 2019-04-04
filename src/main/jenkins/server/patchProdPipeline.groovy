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

def patchConfig = patchfunctions.readPatchFile(params.PARAMETER)
patchfunctions.initPatchConfig(patchConfig,params)

// Load Target System Mappings
def targetSystemsMap = patchfunctions.loadTargetsMap()
println "TargetSystemsMap : ${targetSystemsMap} "
// Create a local Maven Repo for Pipeline
patchfunctions.mavenLocalRepo(patchConfig)
// Retrieve event. State, which will re - done
patchfunctions.redoToState(patchConfig)

// Mainline


// Artefacts are tagged = ready to be built and deployed with start of Patch Pipeline
def target = targetSystemsMap.get('Entwicklung')
patchfunctions.stage(target,"Installationsbereit",patchConfig,"Notification", patchfunctions.&notify)
def phases = targetSystemsMap.keySet()
phases.removeElement('Entwicklung')

phases.each { envName ->
	target = targetSystemsMap.get(envName)
	assert target != null
	patchfunctions.saveTarget(patchConfig,target)

	// Approve to make Patch "Installationsbereit" for target
	patchfunctions.stage(target,"Installationsbereit",patchConfig,"Approve", patchfunctions.&approveBuild)
	lock("${patchConfig.serviceName}${patchConfig.currentTarget}BuildAndAssebly") {
		patchfunctions.stage(target,"Installationsbereit",patchConfig,"Build", patchfunctions.&patchBuildsConcurrent)
		patchfunctions.stage(target,"Installationsbereit",patchConfig,"Assembly", patchfunctions.&assembleDeploymentArtefacts)
	}
	patchfunctions.stage(target,"Installationsbereit",patchConfig,"Notification",  patchfunctions.&notify)
	
	
	// Approve to to install Patch
	
	patchfunctions.stage(target,"Installation",patchConfig,"Approve", patchfunctions.&approveInstallation)
	patchfunctions.stage(target,"Installation",patchConfig,"InstallOldStyle", patchDeployment.&installOldStyle)
	patchfunctions.stage(target,"Installation",patchConfig,"Install", patchDeployment.&installDeploymentArtifacts)
	patchfunctions.stage(target,"Installation",patchConfig,"Postprocess",  patchfunctions.&installationPostProcess)
	patchfunctions.stage(target,"Installation",patchConfig,"Notification",  patchfunctions.&notify)
	
}

