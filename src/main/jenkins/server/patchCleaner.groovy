echo "Patch cleaner starting ..."

def nbMovedjob = 0

node {

	stage("Cleaning patch job") {	
		def JOB_PATTERN = "Patch" //find all jobs starting with "Patch".
		def patchView = hudson.model.Hudson.instance.getView('Patches')
		def productivePatchView = hudson.model.Hudson.instance.getView('ProductivePatches')
		def patchJobs = patchView.getItems()
		
		patchJobs.each { job ->
			
			def jobName = job.name
				
			if(!jobName.endsWith("Download")) {
				def lastSuccesffulbuild = job.getLastSuccessfulBuild()
				if(lastSuccesffulbuild != null) {
					echo "Job ${jobName} successfully ended and will be moved"
					nbMovedjob++
					def NEW_JOB_NAME = "PROD_" + jobName
					
					job.renameTo(NEW_JOB_NAME)
					
					productivePatchView.doAddJobToView(NEW_JOB_NAME)
					
					patchJobs.each{ downloadJob -> 
						if(downloadJob.name.equalsIgnoreCase(jobName + "Download")) {
							// JHE(23.05.2018): Not sure if we really want to delete the download job, for now, just move it as well.
							//jobToBeDeleted.delete()
							def NEW_DOWNLOADJOB_NAME = "PROD_" + downloadJob.name
							downloadJob.renameTo(NEW_DOWNLOADJOB_NAME)
							productivePatchView.doAddJobToView(NEW_DOWNLOADJOB_NAME)
						}
					}
				}
			}
		}
	}
}

echo "Patch cleaner ended and moved ${nbMovedjob} job(s)"