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
def defaultNodes = [[label:env.DEFAULT_JADAS_ONDEMAND_NODE,serviceName:"jadas"]]
def target = [envName:"OnDemand",targetName:patchConfig.installationTarget,nodes:defaultNodes]
patchConfig.currentTarget = patchConfig.installationTarget
patchConfig.targetBean = target

// JHE: TEST TO BE REMOVED !!!!
patchfunctions.stage(target,"Installationsbereit",patchConfig,"Approve", patchfunctions.&jheTest)

//patchfunctions.stage(target,"Installationsbereit",patchConfig,"Build", patchfunctions.&patchBuildsConcurrent)
//patchfunctions.stage(target,"Installationsbereit",patchConfig,"Assembly", patchfunctions.&assembleDeploymentArtefacts)
//patchfunctions.stage(target,"Installation",patchConfig,"Install", patchDeployment.&installDeploymentArtifacts)
