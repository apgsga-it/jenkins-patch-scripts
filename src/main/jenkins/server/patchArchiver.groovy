import hudson.model.*
import jenkins.model.*

def getProductivePatchView() {
	return Hudson.instance.getView('ProductivePatches')
}

def archiveLogsForPipelineJob(def builds, def jobName) {
	def buildNr = 1
	builds.each{ build ->
		def logContent = build.logFile.text
		def logName = "${jobName}-archive-${buildNr}.log"
		new File("/var/opt/apg-jenkins/archive/log/${logName}").write(logContent)
		println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: New archive logged created for job ${jobName} for build ${buildNr} : /var/opt/apg-jenkins/archive/log/${logName}"
		buildNr++
	}
}

def deleteJenkinsWorkspaces(def jobName) {
	// Delete Patch workspace folder within /var/jenkins/workspace
	// JHE (11.06.2018): At best, we would run a command like "rm -rf /var/jenkins/workspace/Patchxxx*" ... but unfortunately we can't use the "*" in the command ... so we have to do some parsing.
	def jenkinsWorkspaceFolder = "/var/jenkins/workspace"
	def lsCmd = "ls -la ${jenkinsWorkspaceFolder} | grep '${jobName}'"
	def lsOutput = ['bash', '-c', lsCmd].execute().in.text
	lsOutput.eachLine{line ->
		def folderToDelete = "${jenkinsWorkspaceFolder}/" + line.substring(line.lastIndexOf(" "), line.length()).trim()
		def rmCmd = "rm -rf ${folderToDelete}"
		['bash', '-c', rmCmd].execute().in.text
		println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: ${folderToDelete} has been deleted!"
	}
}

println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: Patch archiver starting ..."

def nbProdJobDeleted = 0
def nbOnDemandJobDeleted = 0
def productivePatchView = getProductivePatchView()
def patchJobs = productivePatchView.getItems()
def daysToKeepPipeline= build.buildVariableResolver.resolve("daysToKeepPipeline") as Integer
def dateBeforeToDeleteJobs = (new Date()).minus(daysToKeepPipeline)

patchJobs.each { job ->
	def jobName = job.name
	if(jobName  ==~ /Patch+[0123456789]*/ ) {
		def lastSuccesffulbuild = job.getLastSuccessfulBuild().getTime()
		assert job.getLastSuccessfulBuild() != null : println("${job.name} never had any successful job!")
		if (lastSuccesffulbuild.before(dateBeforeToDeleteJobs)) {

			patchJobs.each{ onDemandJob ->
				def onDemandJobName = onDemandJob.name
				if(onDemandJobName.equalsIgnoreCase(jobName + "OnDemand")) {
					if(!onDemandJob.isBuilding()) {

						// Archive all PROD Pipeline logs
						def prodBuilds = Jenkins.getInstance().getItemByFullName(jobName).getBuilds()
						archiveLogsForPipelineJob(prodBuilds,jobName)

						// Archive all OnDemand Pipeline logs
						def downloadBuilds = Jenkins.getInstance().getItemByFullName(onDemandJobName).getBuilds()
						archiveLogsForPipelineJob(downloadBuilds,onDemandJobName)

						job.delete()
						println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: ${jobName} has been deleted."
						nbProdJobDeleted++
						onDemandJob.delete()
						println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: ${onDemandJob} has been deleted"
						nbOnDemandJobDeleted++
					}
				}
			}
			
			deleteJenkinsWorkspaces(jobName)
		}
	}
	
}

println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: Patch archiver done."
println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: ${nbProdJobDeleted} Prod pipeline Job(s) have been deleted."
println "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: ${nbOnDemandJobDeleted} OnDemand pipeline Job(s) have been deleted."