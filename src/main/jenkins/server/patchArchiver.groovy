import hudson.model.*
import jenkins.model.*

def getProductivePatchView() {
	return Hudson.instance.getView('ProductivePatches')
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
						def prodBuildNr = 1
						prodBuilds.each{ prodBuild ->
							def prodLogContent = prodBuild.logFile.text
							def prodLogName = "${jobName}-archive-${prodBuildNr}.log"
							new File("/var/opt/apg-jenkins/archive/log/${prodLogName}").write(prodLogContent)
							prodBuildNr++
						}

						// Archive all Download Pipeline logs
						def downloadBuilds = Jenkins.getInstance().getItemByFullName(downloadJobName).getBuilds()
						def downloadBuildNr = 1
						downloadBuilds.each{ downloadBuild ->
							def downloadLogContent = downloadBuild.logFile.text
							def downloadLogName = "${downloadJobName}-archive-${downloadBuildNr}.log"
							new File("/var/opt/apg-jenkins/archive/log/${downloadLogName}").write(downloadLogContent)
							downloadBuildNr++
						}

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

println "Patch archiver done."
println "${nbProdJobDeleted} Prod pipeline Job(s) have been deleted."
println "${nbDownloadJobDeleted} Download pipeline Job(s) have been deleted."