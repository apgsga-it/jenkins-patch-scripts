import hudson.model.*


def patchView = Hudson.instance.getView('Patches')
def prodPatchView = Hudson.instance.getView('ProductivePatches')
def allView = Hudson.instance.getView('All')

def allJobs = allView.getItems()
def patchjobs = patchView.getItems()
def prodPatchjobs = prodPatchView.getItems()


allJobs.each { job ->
	
	def jobName = job.name
	def addjobToView = true
	
	if(jobName.startsWith("Patch")) {
		
		patchjobs.each {patchjob ->
			def patchjobName = patchjob.name
			if(jobName.equals(patchjobName)) {
				addjobToView = false
			}
		}
		
		if(addjobToView) {
			prodPatchjobs.each {prodPatchjob ->
				def prodPatchjobName = prodPatchjob.name
				if(jobName.equals(prodPatchjobName)) {
					addjobToView = false
				}		
			}
		}
		
	}
	else {
		addjobToView = false
	}
	

	if(addjobToView) {
		println "Job ${jobName} added to Patches view."
		patchjobs.doAddJobToView(jobName)
	}
}