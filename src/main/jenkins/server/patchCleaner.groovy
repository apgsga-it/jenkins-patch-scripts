echo "Patch cleaner starting ..."

def nbMovedjob = 0

def getProductivePatchView() {
	return hudson.model.Hudson.instance.getView('ProductivePatches')
}

node {

	stage("Moving patch job") {	
		def JOB_PATTERN = "Patch" //find all jobs starting with "Patch".
		def patchView = hudson.model.Hudson.instance.getView('Patches')
		def productivePatchView = getProductivePatchView()
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
	
	stage("Cleaning Workspaces") {
		
		/*
		 * JHE (23.05.2018): We iterate over all jobs within "ProductivePatches" View. If a Job is there since more than 2 Weeks, then we clean its workspace
		 */
		
		def productivePatchView = getProductivePatchView()
		def productiveJobs = productivePatchView.getItems()
		
		productiveJobs.each { job ->
			def lastSuccess = job.getLastSuccessfulBuild()
			
			// We might have a null for a download job which would never have ran
			if(lastSuccess != null) {
				
				def comparisonDate = new Date().plus(-14)
				def diff = lastSuccess.getTime() - comparisonDate
				
				
				
				def lastSuccessFormated = lastSuccess.getTime().format("YYYY-MMM-dd HH:MM:SS")
				echo "Last success build for ${job.name} was on ${lastSuccessFormated}, workspace will be clean."
				echo "Diff date = ${diff}"
			}
		}
		
	}
}

echo "Patch cleaner ended and moved ${nbMovedjob} job(s)"