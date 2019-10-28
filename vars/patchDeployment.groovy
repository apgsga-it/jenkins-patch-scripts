#!groovy
library 'patch-global-functions'

def installDeploymentArtifacts(patchConfig) {
	
	// TEST TO BE REMOVED
	/*
	node {

	}
	*/
	/*
	echo "calling closure"
	installerFactory('jadas').call()
	echo "DONE - calling closure"
	*/
	
	
	// ANOTHER ONE TO BE REMOVED
	// TEST FROM SSH connection
	/*
	node {
		echo "trying to do an SSH connection"
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshCredentials',
			usernameVariable: 'SSHUsername', passwordVariable: 'SSHUserpassword']]) {
		
		
		def shCmd = """
					echo 'this is a test'
					echo '-------------'
					ls /opt/apgops
					echo 'this is a 2nd test'
					echo '-------------'
					ls /etc
					"""

		
			def remote = [:]
			remote.name = 'test'
			remote.host = 'dev-jhe.light.apgsga.ch'
			remote.user = SSHUsername
			remote.password = SSHUserpassword
			remote.allowAnyHosts = true
			sshCommand remote: remote, command: shCmd
		}
		echo "DONE - trying to do an SSH connection"
	}
	*/
	
	
	
	
	
	
	
	
	lock("${patchConfig.serviceName}${patchConfig.currentTarget}Install") {
		// CM-225: old style needs to be part of the "installLock" (It can not run parallel to "db-deployment")
		echo "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: Starting installOldStyle"
		installOldStyle(patchConfig)
		echo "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}: Done installOldStyle"
		parallel 'ui-client-deployment': {
			installerFactory('it21_ui', patchConfig.currentTarget).call()
			/*
			if(patchConfig.installJadasAndGui) {
				node {
					installJadasGUI(patchConfig)
				}
			}
			*/
		}, 'ui-server-deployment': {
			installerFactory('jadas', patchConfig.currentTarget).call()
			/*
			if(patchConfig.installJadasAndGui) {
				echo "patchConfig.targetBean = ${patchConfig.targetBean}"
				def installationNodeLabel = patchfunctions.serviceInstallationNodeLabel(patchConfig.targetBean,"jadas")
				echo "Installation of jadas Service will be done on Node : ${installationNodeLabel}"
				println "SSH DEBUG : ui-server-deployment via SSH"
				
				node (installationNodeLabel){
					echo "Installation of apg-jadas-service-${patchConfig.currentTarget} starting ..."
					def yumCmdOptions = "--disablerepo=* --enablerepo=apg-artifactory*"
					def yumCmd = "sudo yum clean all ${yumCmdOptions} && sudo yum -y install ${yumCmdOptions} apg-jadas-service-${patchConfig.currentTarget}"
					sh "echo \$( date +%Y/%m/%d-%H:%M:%S ) - executing with \$( whoami )@\$( hostname )"
					sh "${yumCmd}"
					echo "Installation of apg-jadas-service-${patchConfig.currentTarget} done!"
				}
			}
			*/
		}, 'db-deployment': {
			installerFactory('it21-db', patchConfig.currentTarget).call()
			/*
			node {
				installDbPatch(patchConfig,patchfunctions.getCoPatchDbFolderName(patchConfig),"zip",getHost("it21-db",patchConfig.currentTarget),getType("it21-db",patchConfig.currentTarget))
			}
			*/
		}
	}
}

def installerFactory(serviceName,target) {
	// JHE (28.10.2019): For now we only support 3 services to be installed over SSH. Do we really want an assert, or rather an empty implementation for any unkown type?
	//					 Or don't we need this test, and do we want to rely 100% on what's define within TargetSystemMappings.json? (if a service is not find, then fail, or skip, or return NOP implementation)
	assert ['it21-db','jadas','it21_ui'].contains(serviceName) : "Installation of ${serviceName} not yet supported!"
	
	def serviceType = getType(serviceName,target)
	
	if(serviceType.equals("linuxservice")) {
		return linuxServiceInstaller()
	}
	else if(serviceType.equals("it21_ui")) {
		return it21UiInstaller()
	}
	else if(serviceType.equals("oracle-db")) {
		return oracleDbInstaller()
	}
	else {
		return nopInstaller()
	}
}

def linuxServiceInstaller() {
	def installer = {
		echo 'This is a linux service installer'
	}
}

def it21UiInstaller() {
	def installer = {
		echo 'This is a it21-ui service installer'
	}
}

def oracleDbInstaller() {
	def installer = {
		echo 'This is an oracle-db service installer'
	}
}

def nopInstaller() {
	def installer = {
		echo 'No installer define for this service: todo -> add service name'
	}
}

def getHost(service,target) {
	def s = getTargetInstanceService(service, target)
	assert s.host != null : "Host has not been configured for target ${target}"
	return s.host
}

def getType(service,target) {
	def s = getTargetInstanceService(service, target)
	assert s.type != null : "Type of service has not been configured for target ${target}"
	return s.type
}

def getTargetInstanceService(service,target) {
	def targetSystemMappingJson = patchfunctions.getTargetSystemMappingJson()
	def targetInstance = patchfunctions.getTargetInstance(target,targetSystemMappingJson)
	assert targetInstance != null : "${target} should be configured as a targetInstance!"
	def s = targetInstance.services.find{it.name == service}
	assert s != null : "${target} is configured as a targetInstance, but service ${service} has not been configured."
	return s
}

def installOldStyle(patchConfig) {
	installOldStyleInt(patchConfig,"db","zip")
}

def installOldStyleInt(patchConfig,artifact,extension) {
	def server = initiateArtifactoryConnection()
	
	println "SSH DEBUG : installOldStyleInt via SSH"
		
	/*
	node (env.WINDOWS_INSTALLER_OLDSTYLE_LABEL){
			
		// jenkins_pipeline_patch_install_oldstyle starts also the installation of Docker Services
		bat("cmd /c c:\\local\\software\\cm_winproc_root\\it21_extensions\\jenkins_pipeline_patch_install_oldstyle.bat ${patchConfig.patchNummer} ${patchConfig.currentTarget}")
	}
	*/
}

def installDbPatch(patchConfig,artifact,extension,host,type) {
	def server = initiateArtifactoryConnection()
	def patchDbFolderName = patchfunctions.getCoPatchDbFolderName(patchConfig)
	
	println "SSH DEBUG : installDbPatch via SSH"
	
	/*
	node (env.WINDOWS_INSTALLER_LABEL){
		
		def downloadSpec = """{
              "files": [
                    {
                     "pattern": "${env.DB_PATCH_REPO}*${artifact}.${extension}",
					 "target": "download/"
					 }
				   ]
			}"""
		server.download(downloadSpec)
		
		
		unzip zipFile: "download/${artifact}.${extension}"
		
		// Here will only the "CVS DB" module installed.
		bat("cmd /c c:\\local\\software\\cm_winproc_root\\it21_extensions\\jenkins_pipeline_patch_install.bat ${patchDbFolderName} ${patchConfig.currentTarget} ${host} ${type}")
	}
	*/
}

def getCredentialId(def patchConfig) {
	if(patchConfig.currentTarget.toLowerCase().startsWith("cht")) {
		return "svcIt21Install-t"
	}
	else {
		return "svcit21install"
	}
}

def installJadasGUI(patchConfig) {
	
	println "SSH DEBUG : installJadasGUI via SSH"
	
	/*
	node(env.WINDOWS_INSTALLER_LABEL) {
		
		def extractedGuiPath = ""

		extractedGuiPath = "\\\\service-${patchConfig.currentTarget}.apgsga.ch\\it21_${patchConfig.currentTarget}_gui"

		def credentialId = getCredentialId(patchConfig)
		
		withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialId,
					usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
	
				// Mount the share drive
				powershell("net use ${extractedGuiPath} ${PASSWORD} /USER:${USERNAME}")
		}

		def buildVersion =  patchfunctions.mavenVersionNumber(patchConfig,patchConfig.revision)
		def group = "com.affichage.it21"
		def artifact = "it21gui-dist-zip"
		def artifactType = "zip"

		downloadGuiZipToBeInstalled(group,artifact,artifactType,buildVersion)

		def extractedFolderName = guiExtractedFolderName()
		
		def zipDist = "${artifact}-${buildVersion}.${artifactType}"
		extractGuiZip(zipDist,extractedGuiPath,extractedFolderName)
		copyGuiOpsResources(patchConfig,extractedGuiPath,extractedFolderName)
		println "Waiting 60 seconds before trying to rename the extracted ZIP."
		sleep(time:60,unit:"SECONDS")
		renameExtractedGuiZip(extractedGuiPath,extractedFolderName)
		println "GUI Folder correctly renamed."
		copyCitrixBatchFile(extractedGuiPath,extractedFolderName)
		removeOldGuiFolder(extractedGuiPath)

		// Unmount the share drive
		powershell("net use ${extractedGuiPath} /delete")
	}
	*/
}

def removeOldGuiFolder(extractedGuiPath) {
	// Keep last 2 folders starting with java_gui.
	def guiFolderNamePrefix = "java_gui"
	def nbFolderToKeep = "10"
	powershell "Get-ChildItem ${extractedGuiPath} -Directory -Recurse -Include ${guiFolderNamePrefix}* | Sort-Object CreationTime -Descending | Select-Object -Skip ${nbFolderToKeep} | Remove-Item -Recurse -Force"
	
}

def guiExtractedFolderName() {
	def currentDateAndTime = new Date().format('yyyyMMddHHmmss')
	def extractedFolderName = "java_gui_${currentDateAndTime}"
	return extractedFolderName
}

def downloadGuiZipToBeInstalled(def groupId, def artifactId, def artifactType, def buildVersion) {
	def mvnCommand = "mvn dependency:copy -Dartifact=${groupId}:${artifactId}:${buildVersion}:${artifactType} -DoutputDirectory=./download -s C:/local/software/maven/settings.xml"
	echo "Downloading GUI-ZIP with following command: ${mvnCommand}"
	withMaven( maven: 'apache-maven-3.5.0') { bat "${mvnCommand}" }
	echo "GUI-ZIP correctly downloaded."
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
	dir("C:\\config\\${patchConfig.currentTarget}\\it21-gui") {
		fileOperations ([fileCopyOperation(flattenFiles: true, excludes: '', includes: '*.properties', targetLocation: "${extractedGuiPath}\\getting_extracted_${extractedFolderName}\\conf")])
	}
}