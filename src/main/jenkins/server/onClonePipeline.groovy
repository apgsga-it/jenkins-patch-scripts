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
			echo "Within resetRevision stage ...."
		}
	}
	
	stage("cleanArtifactory") { 
		node {
			echo "Within cleanArtifactory stage ...."
		}
	}
}