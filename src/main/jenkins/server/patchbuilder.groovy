import hudson.model.*

def patchName = "Patch${patchnumber}"
def jobName = patchName
def downLoadJobName = jobName + "Download"

pipelineJob (jobName) {
	authenticationToken(patchName)
	concurrentBuild(false)
	definition {
		cps {
			script(readFileFromWorkspace('src/main/jenkins/server/patchProdPipeline.groovy'))
			sandbox(false)
		}
	}
	logRotator(5,10,5,-1)
	description("Patch Pipeline for : ${patchName}")
	parameters {
		stringParam('PARAMETER', "", "String mit dem die PatchConfig Parameter als JSON transportiert werden")
	}
	
	// JHE (04.06.2018): Ideally, one would use listView section here. The problem with listView is that it adds new job(s), but loose all job which were already listed. 
	def patchView = Hudson.instance.getView("Patches")
	patchView.doAddJobToView(jobName)
}
pipelineJob (downLoadJobName) {
	authenticationToken(downLoadJobName)
	concurrentBuild(false)
	definition {
		cps {
			script(readFileFromWorkspace('src/main/jenkins/server/patchDownloadPipeline.groovy'))
			sandbox(false)
		}
	}
	logRotator(5,10,5,-1)
	description("*Download* Patch Pipeline for : ${patchName}")
	parameters {
		stringParam('PARAMETER', "", "String mit dem die PatchConfig Parameter als JSON transportiert werden")
	}
	
	// JHE (04.06.2018): Ideally, one would use listView section here. The problem with listView is that it adds new job(s), but loose all job which were already listed. 
	def patchView = Hudson.instance.getView("Patches")
	patchView.doAddJobToView(downLoadJobName)
}