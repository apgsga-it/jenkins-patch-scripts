echo "Patch cleaner starting ..."

node {
	// Clean workspace
	//cleanWs()
		
	
	def JOB_PATTERN = "Patch" //find all jobs starting with "Patch".
	def nbMovedjob = 0
	
	def patchView = hudson.model.Hudson.instance.getView('Patches')
	def productivePatchView = hudson.model.Hudson.instance.getView('ProductivePatches')
	
	def patchJobs = patchView.getItems()
		
	patchJobs.each { job ->
		
		if(!job.name.equalsIgnoreCase("PatchBuilder") && !job.name.equalsIgnoreCase("PatchCleaner")) {
			
			
			if(!job.name.endsWith("Download")) {
				def lastSuccesffulbuild = job.getLastSuccessfulBuild()
				if(lastSuccesffulbuild != null) {
					echo "Job ${job.name} successfully ended and will be moved"
					nbMovedjob++
					def NEW_JOB_NAME = "PROD_${job.name}"
					
					productivePatchView.doAddJobToView(NEW_JOB_NAME)
					
					//patchView.doRemoveJobFromView("${job.name}Download")
					
				}
			}
		}
	}
	
	
	/*
	// Rename and move jobs
	def JOB_PATTERN = "Patch${patchConfig.patchNummer}"; //find all jobs starting with "MY_JOB".
	def NEW_PART = "PROD_"
	(Hudson.instance.items.findAll { job -> job.name =~ JOB_PATTERN }).each { job_to_update ->
		def NEW_JOB_NAME = NEW_PART + job_to_update.name
		echo("Updating job " + job_to_update.name);
		echo("New name: " + NEW_JOB_NAME);
		job_to_update.renameTo(NEW_JOB_NAME);
		echo("Updated name: " + job_to_update.name);
		
		//If it's the "download job", then we delete it
		if(job_to_update.name.endsWith("Download")) {
			job_to_update.delete()
		}
		else {
			echo("Now moving ${NEW_JOB_NAME} under productive Patch view")
			def productivePatchView = hudson.model.Hudson.instance.getView('ProductivePatches')
			productivePatchView.doAddJobToView(NEW_JOB_NAME)
			// JHE (08.05.2018): the below is not necessary. Patches view list job based on Regex, not by direclty including jobs.
			//def patchView = hudson.model.Hudson.instance.getView('Patches')
			//myView2.doRemoveJobFromView(NEW_JOB_NAME)
		}

	}
	*/
}

echo "Patch cleaner ended and moved ${nbMovedjob} job(s)"