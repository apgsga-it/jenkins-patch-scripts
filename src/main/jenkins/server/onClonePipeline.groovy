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
			echo "Within resetRevision stage .... job will be done for ${target}"
			def result = sh returnStatus: true, script: "/opt/apg-patch-cli/bin/apscli.sh -resr ${target}"
			assert result == 0 : println ("Error while resetting revision for ${target}")
		}
	}
	
	stage("cleanArtifactory") { 
		node {
			echo "Within cleanArtifactory stage ...."
		}
	}
}