def installDeploymentArtifacts(patchConfig) {
	lock("${patchConfig.serviceName}${patchConfig.installationTarget}Install") {
		parallel 'ui-client-deployment': {
			node {install(patchConfig,"client","it21gui-dist-zip","zip")}
		}, 'ui-server-deployment': {
			node {install(patchConfig,"docker",patchConfig.jadasServiceArtifactName,patchConfig.dockerBuildExtention) }
		}
	}
}

def install(patchConfig, type, artifact,extension) {
	if (!type.equals("docker")) {

		if(!patchConfig.installationTarget.equalsIgnoreCase("CHEI212")) {
			echo "GUI can currently only be installed on CHEI212. ${patchConfig.installationTarget} not supported yet."
			return
		}

		installGUI(patchConfig,artifact,extension)
	}
	else {
		if(!artifact.equals(patchConfig.jadasServiceArtifactName)) {
			echo "Don't know how to install services apart from jadas-service : TODO"
			return
		}

		def dropName = jadasServiceDropName(patchConfig)
		def dockerDeploy = "/opt/apgops/docker/deploy.sh jadas-service ${patchConfig.patchNummer}-${patchConfig.revision}-${BUILD_NUMBER} ${patchConfig.installationTarget}"
		echo dockerDeploy
		sh "${dockerDeploy}"
	}

}

def installGUI(patchConfig,artifact,extension) {
	node(env.JENKINS_NODE_DEV) {

		def extractedGuiPath = "\\\\gui-${patchConfig.installationTarget}.apgsga.ch\\it21_${patchConfig.installationTarget}"

		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'svcit21install',
				usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

			// Mount the share drive
			powershell("net use ${extractedGuiPath} ${PASSWORD} /USER:${USERNAME}")
		}

		def artifactoryServer = initiateArtifactoryConnection()

		def buildVersion =  mavenVersionNumber(patchConfig,patchConfig.revision)
		def zip = "${artifact}-${buildVersion}.${extension}"

		//TODO JHE: here we should probably pass the repo type as well -> snapshot or relaease, althought it might always be relaease...
		downloadGuiZipToBeInstalled(artifactoryServer,zip)

		def extractedFolderName = guiExtractedFolderName()

		extractGuiZip(zip,extractedGuiPath,extractedFolderName)
		renameExtractedGuiZip(extractedGuiPath,extractedFolderName)
		copyGuiOpsResources(patchConfig,extractedGuiPath,extractedFolderName)
		copyCitrixBatchFile(extractedGuiPath,extractedFolderName)

		// Unmount the share drive
		powershell("net use ${extractedGuiPath} /delete")
	}
}

def guiExtractedFolderName() {
	def currentDateAndTime = new Date().format('yyyyMMddHHmmss')
	def extractedFolderName = "java_gui_${currentDateAndTime}"
	return extractedFolderName
}

def downloadGuiZipToBeInstalled(artifactoryServer,zip) {
	def downloadSpec = """{
              "files": [
                    {
                      "pattern": "releases/*${zip}",
					   "target": "download/"
					   }
			 ]
	}"""
	artifactoryServer.download(downloadSpec)
}

def initiateArtifactoryConnection() {
	//def server = Artifactory.server 'artifactory4t4apgsga' // prerequisite: needs to be configured on Jenkins
	def server = Artifactory.server env.ARTIFACTORY_SERVER_ID

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactoryDev',
			usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

		server.username = "${USERNAME}"
		server.password = "${PASSWORD}"
	}

	return server
}

def extractGuiZip(downloadedZip,extractedGuiPath,extractedFolderName) {
	def files = findFiles(glob: "**/${downloadedZip}")
	unzip zipFile: "${files[0].path}", dir: "${extractedGuiPath}\\getting_extracted_${extractedFolderName}"


}

def copyCitrixBatchFile(extractedGuiPath,extractedFolderName) {
	// We need to move one bat one level-up -> this is the batch which will be called from Citrix
	dir("${extractedGuiPath}\\${extractedFolderName}") {
		fileOperations ( [fileCopyOperation(flattenFiles: true, excludes: '', includes: '*start_it21_gui_run.bat', targetLocation: "${extractedGuiPath}"), fileDeleteOperation(includes: '*start_it21_gui_run.bat', excludes: '')])
	}
}

def renameExtractedGuiZip(extractedGuiPath,extractedFolderName) {
	fileOperations ([folderRenameOperation(source: "${extractedGuiPath}\\getting_extracted_${extractedFolderName}", destination: "${extractedGuiPath}\\${extractedFolderName}")])
}

def copyGuiOpsResources(patchConfig,extractedGuiPath,extractedFolderName) {
	dir("C:\\config\\${patchConfig.installationTarget}\\it21-gui") {
		fileOperations ([fileCopyOperation(flattenFiles: true, excludes: '', includes: '*.properties', targetLocation: "${extractedGuiPath}\\${extractedFolderName}\\conf")])
	}
}