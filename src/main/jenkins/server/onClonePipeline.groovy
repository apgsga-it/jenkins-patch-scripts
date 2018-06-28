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
	
	stage("resetRevision") {
		node {
			def target = params.target
			echo "Revision will be reset for target ${target}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -resr ${target}"
			assert result == 0 : println ("Error while resetting revision for ${target}")
		}
	}
	
	stage("cleanArtifactory") { 
		node {
			def target = params.target 
			echo "Cleaning Artifactory for revisions build for target ${target}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -cr ${target}"
			assert result == 0 : println ("Error while clean Artifactory revision for target ${target}")
		}
	}
}