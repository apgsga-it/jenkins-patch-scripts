def patchName = "Patch${patchnumber}"
def jobName = patchName
def downLoadJobName = jobName + "Download"
pipelineJob (jobName) {
	authenticationToken(patchName)
	concurrentBuild(false)
	definition {
		cps {
			script(readFileFromWorkspace('jenkins-patch-scripts/src/main/jenkins/server/patchProdPipeline.groovy'))
			sandbox(false)
		}
	}
	logRotator(5,10,5,-1)
	description("Patch Pipeline for : ${patchName}")
	parameters {
		stringParam('PARAMETER', "", "String mit dem die PatchConfig Parameter als JSON transportiert werden")
	}
}
pipelineJob (downLoadJobName) {
	authenticationToken(downLoadJobName)
	concurrentBuild(false)
	definition {
		cps {
			script(readFileFromWorkspace('jenkins-patch-scripts/src/main/jenkins/server/patchDownloadPipeline.groovy'))
			sandbox(false)
		}
	}
	logRotator(5,10,5,-1)
	description("*Download* Patch Pipeline for : ${patchName}")
	parameters {
		stringParam('PARAMETER', "", "String mit dem die PatchConfig Parameter als JSON transportiert werden")
	}
}