import hudson.model.*
import jenkins.model.*

def getProductivePatchView() {
	return Hudson.instance.getView('ProductivePatches')
}

def archiveLogsForPipelineJob(def builds) {
	def buildNr = 1
	builds.each{ build ->
		def logContent = build.logFile.text
		def logName = "${jobName}-archive-${buildNr}.log"
		new File("/var/opt/apg-jenkins/archive/log/${logName}").write(logContent)
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
		println "${folderToDelete} has been deleted!"
	}
}

println "Patch archiver starting ..."

def nbProdJobDeleted = 0
def nbDownloadJobDeleted = 0
def productivePatchView = getProductivePatchView()
def patchJobs = productivePatchView.getItems()
def daysToKeepPipeline= build.buildVariableResolver.resolve("daysToKeepPipeline") as Integer
def dateBeforeToDeleteJobs = (new Date()).minus(daysToKeepPipeline)

patchJobs.each { job ->
	def jobName = job.name
	if(!jobName.endsWith("Download")) {
		def lastSuccesffulbuild = job.getLastSuccessfulBuild().getTime()

		if (lastSuccesffulbuild.before(dateBeforeToDeleteJobs)) {

			patchJobs.each{ downloadJob ->
				def downloadJobName = downloadJob.name
				if(downloadJobName.equalsIgnoreCase(jobName + "Download")) {
					if(!downloadJob.isBuilding()) {

						// Archive all PROD Pipeline logs
						def prodBuilds = Jenkins.getInstance().getItemByFullName(jobName).getBuilds()
						archiveLogsForPipelineJob(prodBuilds)

						// Archive all Download Pipeline logs
						def downloadBuilds = Jenkins.getInstance().getItemByFullName(downloadJobName).getBuilds()
						archiveLogsForPipelineJob(downloadBuilds)

						job.delete()
						println "${jobName} has been deleted."
						nbProdJobDeleted++
						downloadJob.delete()
						println "${downloadJobName} has been deleted"
						nbDownloadJobDeleted++
					}
				}
			}
		}
	}
	
	deleteJenkinsWorkspaces(jobName)
}

println "Patch archiver done."
println "${nbProdJobDeleted} Prod pipeline Job(s) have been deleted."
println "${nbDownloadJobDeleted} Download pipeline Job(s) have been deleted."