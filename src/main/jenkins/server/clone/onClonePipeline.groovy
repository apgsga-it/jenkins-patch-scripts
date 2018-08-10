package clone
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

properties([
	parameters([
		stringParam(
		defaultValue: "",
		description: 'Parameter',
		name: 'target'
		)
	])
])

stage("onclone") {
	
	stage("cleanArtifactory") {
		node {
			def target = params.target
			echo "Cleaning Artifactory for revisions build for target ${target}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -cr ${target}"
			assert result == 0 : println ("Error while clean Artifactory revision for target ${target}")
		}
	}
	
	stage("resetRevision") {
		node {
			def target = params.target
			echo "Revision will be reset for target ${target}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -resr ${target}"
			assert result == 0 : println ("Error while resetting revision for ${target}")
		}
	}
	
	stage("startReinstallPatchPipeline") {
		node {
			echo "Verifying if ${params.target} requires patch to be automatically re-installed..."

			// Check if target corresponds to a status for which we automatically install patches (Informatiktest only for now)
			def status = getStatusForTarget()
			if(status == null || !status.toString().equalsIgnoreCase("informatiktest")) {
				echo "No patch have to be re-installed on ${params.target}. ${params.target} is not configured as Informatiktest target."
			}
			else {
				echo "Patch have to be re-installed on ${params.target}, reinstallPatchAfterClone Pipeline will be started"
				build job: 'reinstallPatchAfterClone', parameters: [string(name: 'target', value: params.target)]
			}
		}
	}
}

private def getStatusForTarget() {
	def targetSystemFile = new File("/etc/opt/apg-patch-common/TargetSystemMappings.json")
	assert targetSystemFile.exists() : println ("/etc/opt/apg-patch-common/TargetSystemMappings.json doesn't exist or is not accessible!")
	def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
	def status
	
	jsonSystemTargets.targetSystems.each{ targetSystem ->
		if(targetSystem.target.equalsIgnoreCase(params.target)) {
			status = targetSystem.name
		}
	}
	
	return status
}