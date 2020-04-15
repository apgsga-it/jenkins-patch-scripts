import hudson.model.*

def nbMovedjob = 0

def getProductivePatchView() {
	return Hudson.instance.getView('ProductivePatches')
}

def getPatchView() {
	return Hudson.instance.getView('Patches')
}
println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: Patch cleaner starting ..."

def patchView = getPatchView()
def productivePatchView = getProductivePatchView()
def patchJobs = patchView.getItems()

patchJobs.each { job ->
	def jobName = job.name
	if(!jobName.endsWith("OnDemand")) {
		def lastSuccesffulbuild = job.getLastSuccessfulBuild()
		if(lastSuccesffulbuild != null && !job.isBuilding()) {
			println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: Job ${jobName} successfully ended and will be moved"
			nbMovedjob++
			productivePatchView.doAddJobToView(jobName)
			patchView.doRemoveJobFromView(jobName)
			patchJobs.each{ downloadJob -> 
				if(downloadJob.name.equalsIgnoreCase(jobName + "OnDemand")) {
					productivePatchView.doAddJobToView(downloadJob.name)
					patchView.doRemoveJobFromView(downloadJob.name)
				}
			}
		}
	}  
}

println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: PatchCleaner ended, ${nbMovedjob} job(s) have been moved."