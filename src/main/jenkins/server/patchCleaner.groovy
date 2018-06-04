import hudson.model.*

def nbMovedjob = 0

def getProductivePatchView() {
	return Hudson.instance.getView('ProductivePatches')
}

def getPatchView() {
	return Hudson.instance.getView('Patches')
}

println "Patch cleaner starting ..."

def patchView = getPatchView()
def productivePatchView = getProductivePatchView()
def patchJobs = patchView.getItems()

patchJobs.each { job ->
	def jobName = job.name
	if(!jobName.endsWith("Download")) {
		def lastSuccesffulbuild = job.getLastSuccessfulBuild()
		if(lastSuccesffulbuild != null && !job.isBuilding()) {
			println "Job ${jobName} successfully ended and will be moved"
			nbMovedjob++
			productivePatchView.doAddJobToView(jobName)
			patchView.doRemoveJobFromView(jobName)
			patchJobs.each{ downloadJob -> 
				if(downloadJob.name.equalsIgnoreCase(jobName + "Download")) {
					// JHE(23.05.2018): Not sure if we really want to delete the download job, for now, just move it as well.
					//jobToBeDeleted.delete()
					productivePatchView.doAddJobToView(downloadJob.name)
					patchView.doRemoveJobFromView(downloadJob.name)
				}
			}
		}
	}  
}

println "PatchCleaner ended, ${nbMovedjob} job(s) have been moved."