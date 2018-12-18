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

def patchConfig = patchfunctions.readPatchFile(params.PARAMETER)
patchfunctions.initPatchConfig(patchConfig,params)

// Mainline
def target = [envName:"OnDemand",targetName:patchConfig.installationTarget]
patchfunctions.stage(target,"Installationsbereit",patchConfig,"Build", patchfunctions.&patchBuildsConcurrent)
patchfunctions.stage(target,"Installationsbereit",patchConfig,"Assembly", patchfunctions.&assembleDeploymentArtefacts)
patchfunctions.stage(target,"Installation",patchConfig,"Install", patchDeployment.&installDeploymentArtifacts)
