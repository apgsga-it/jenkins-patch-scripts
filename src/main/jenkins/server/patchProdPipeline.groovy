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

// Process Parameters
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
// Retrieve event. State, which will re - done
patchfunctions.redoToState(patchConfig)

// Mainline


// Artefacts are tagged = ready to be buildt and deployed with start of Patch Pipeline
def target = targetSystemsMap.get('Entwicklung')
patchfunctions.stage(target,"Installationsbereit",patchConfig,"Notification", patchfunctions.notify(patchConfig))

['Informatiktest', 'Produktion'].each { envName ->
	target = targetSystemsMap.get(envName)
	assert target != null
	patchfunctions.targetIndicator(patchConfig,target)

	// Approve to make Patch "Installationsbereit" for target
	patchfunctions.stage(target,"Installationsbereit",patchConfig,"Approve", patchfunctions.approveBuild(patchConfig))
	patchfunctions.stage(target,"Installationsbereit",patchConfig,"Build", patchfunctions.patchBuildsConcurrent(patchConfig))
	patchfunctions.stage(target,"Installationsbereit",patchConfig,"Assembly", patchfunctions.assembleDeploymentArtefacts(patchConfig))
	patchfunctions.stage(target,"Installationsbereit",patchConfig,"Notification",  patchfunctions.notify(patchConfig))
	
	// Approve to to install Patch
	
	patchfunctions.stage(target,"Installation",patchConfig,"Approve", patchfunctions.approveInstallation(patchConfig))
	patchfunctions.stage(target,"Installation",patchConfig,"Install", patchDeployment.installDeploymentArtifacts(patchConfig))
	patchfunctions.stage(target,"Installation",patchConfig,"Notification",  patchfunctions.installationPostProcess(patchConfig))
	patchfunctions.stage(target,"Installation",patchConfig,"Notification",  patchfunctions.notify(patchConfig))
	
}

