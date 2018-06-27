stage("onclone") {
	
	stage("resetRevision") {
		node {
			echo "Within resetRevision stage ...."
		}
	}
	
	stage("cleanArtifactory") { 
		node {
			echo "Within cleanArtifactory stage ...."
		}
	}
}