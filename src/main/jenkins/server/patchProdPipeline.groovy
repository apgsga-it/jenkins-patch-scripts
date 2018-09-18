#!groovy
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
// TODO  (che, 9.7) When JENKINS-27413 is resolved
// Passing Patch File Path , because of JAVA8MIG-395 / JENKINS-27413
def patchFile = new File(params.PARAMETER)
def patchConfig = new JsonSlurperClassic().parseText(patchFile.text)
echo patchConfig.toString()
patchConfig.cvsroot = env.CVS_ROOT
patchConfig.jadasServiceArtifactName = "com.affichage.it21:it21-jadas-service-dist-gtar"
patchConfig.dockerBuildExtention = "tar.gz"
patchConfig.patchFilePath = params.PARAMETER

// Load Target System Mappings
def targetSystemsMap = patchfunctions.loadTargetsMap()
println "TargetSystemsMap : ${targetSystemsMap} " 
patchfunctions.mavenLocalRepo(patchConfig)
println patchConfig.mavenLocalRepo
// Mainline
// While mit Start der Pipeline bereits getagt ist

def target = targetSystemsMap.get('Entwicklung')
stage("${target.envName} (${target.targetName}) Installationsbereit Notification") {
	patchfunctions.notify(target,"Installationsbereit", patchConfig)
}

[
	'Informatiktest',
	'Produktion'
].each { envName ->
	target = targetSystemsMap.get(envName)
	assert target != null
	patchfunctions.targetIndicator(patchConfig,target)
	stage("Approve ${envName} (${target.targetName}) Build & Assembly") { patchfunctions.approveBuild(patchConfig) }
	stage("${envName} (${target.targetName}) Build" ) { patchfunctions.patchBuildsConcurrent(patchConfig)  }
	stage("${envName} (${target.targetName}) Assembly" ) { patchfunctions.assembleDeploymentArtefacts(patchConfig) }
	stage("${envName} (${target.targetName}) Installationsbereit Notification") {
		patchfunctions.notify(target,"Installationsbereit", patchConfig)
	}
	stage("Approve ${envName} (${target.targetName}) Installation") { patchfunctions.approveInstallation(patchConfig)	 }
	stage("${envName} (${target.targetName}) Installation") { patchDeployment.installDeploymentArtifacts(patchConfig)  }
	stage("${envName} (${target.targetName}) Installation Notification") {
		patchfunctions.mergeDbObjectOnHead(patchConfig, envName)
		patchfunctions.notify(target,"Installation", patchConfig)
	}
}