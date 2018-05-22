echo "Patch cleaner starting ..."

def nbMovedjob = 0

node {

	stage("Cleaning patch job") {	
		def JOB_PATTERN = "Patch" //find all jobs starting with "Patch".
		def patchView = hudson.model.Hudson.instance.getView('Patches')
		def productivePatchView = hudson.model.Hudson.instance.getView('ProductivePatches')
		def patchJobs = patchView.getItems()
		
		patchJobs.each { job ->
			
			if(!job.name.equalsIgnoreCase("PatchBuilder") && !job.name.equalsIgnoreCase("PatchCleaner")) {
				
				def jobName = job.name
				
				if(!jobName.endsWith("Download")) {
					def lastSuccesffulbuild = job.getLastSuccessfulBuild()
					if(lastSuccesffulbuild != null) {
						echo "Job ${jobName} successfully ended and will be moved"
						nbMovedjob++
						def NEW_JOB_NAME = "PROD_" + jobName
						
						job.renameTo(NEW_JOB_NAME)
						
						productivePatchView.doAddJobToView(NEW_JOB_NAME)
						
						patchJobs.each{ jobToBeDeleted -> 
							if(jobToBeDeleted.name.equalsIgnoreCase(jobName + "Download")) {
								jobToBeDeleted.delete()
							}
						}
					}
				}
			}
		}
	}
}

echo "Patch cleaner ended and moved ${nbMovedjob} job(s)"