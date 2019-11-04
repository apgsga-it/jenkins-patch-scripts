#!groovy
library 'patch-global-functions'

def installDeploymentArtifacts(patchConfig) {
	lock("${patchConfig.serviceName}${patchConfig.currentTarget}Install") {
		// CM-225: old style needs to be part of the "installLock" (It can not run parallel to "db-deployment")
		patchfunctions.log("Starting installOldStyle","installDeploymentArtifacts")
		installOldStyle(patchConfig)
		patchfunctions.log("Done installOldStyle","installDeploymentArtifacts")
		parallel 'ui-client-deployment': {
			if(patchConfig.installJadasAndGui) {
				installerFactory('it21_ui', patchConfig).call()
				// JHE (29.10.2019): In a first step, we still do the installation following old method
				if(!isLightInstallation(patchConfig.currentTarget)) {
					node {
						installJadasGUI(patchConfig)
					}
				}
			}
		}, 'ui-server-deployment': {
			// JHE: Ignoring this step while testing GUI Install...
			/*
			if(patchConfig.installJadasAndGui) {
				installerFactory('jadas', patchConfig).call()
			}
			*/
		}, 'db-deployment': {
			// JHE (29.10.2019): DB part is not yet ready to be installed with SSH
			// installerFactory('it21-db', patchConfig.currentTarget).call()
			
			// JHE: Ignoring this step while testing GUI Install...
			/*
			node {
				installDbPatch(patchConfig,patchfunctions.getCoPatchDbFolderName(patchConfig),"zip",getHost("it21-db",patchConfig.currentTarget),getType("it21-db",patchConfig.currentTarget))
			}
			*/
		}
	}
}

// JHE (31.10.2019): For now we're still installing the it21-ui using old method, which doesn't work on Light.
//					 It will be removed as soon as we're 100% sure the new method correctly works. 
def isLightInstallation(target) {
	def targetSystemMappingJson = patchfunctions.getTargetSystemMappingJson()
	def isLight = false
	targetSystemMappingJson.targetInstances.each ({ targetInstance ->
		if(targetInstance.name == target) {
			targetInstance.services.each ({ service ->
				if(service.name == "it21-db") {
					isLight = service.host.contains("light")
				}
			})
		}
	})
	patchfunctions.log("is ${target} a Light-Instance: ${isLight}","isLightInstallation")
	isLight
}

def installerFactory(serviceName,patchConfig) {
	// JHE (28.10.2019): For now we only support 2 services to be installed over SSH. Anything else wouldn't be ready yet
	assert ['jadas','it21_ui'].contains(serviceName) : "Installation of ${serviceName} not yet supported!"
	
	def target = patchConfig.currentTarget
	def serviceType = getType(serviceName,target)
	def host = getHost(serviceName, target)
	
	if(serviceType.equals("linuxservice")) {
		return linuxServiceInstaller(target,host)
	}
	else if(serviceType.equals("linuxbasedwindowsfilesystem")) {
		def buildVersion = patchfunctions.mavenVersionNumber(patchConfig,patchConfig.revision)
		return it21UiInstaller(target,host,buildVersion)
	}
	else {
		return nopInstaller(serviceName)
	}
}

def getRemoteSSHConnection(host) {
	
	def remote = [:]
	remote.name = "SSH-${host}"
	remote.host = host
	remote.allowAnyHosts = true
	
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sshCredentials',
		usernameVariable: 'SSHUsername', passwordVariable: 'SSHUserpassword']]) {

			remote.user = SSHUsername
			remote.password = SSHUserpassword
	}
	
	return remote
}

def linuxServiceInstaller(target, host) {
	def installer = {
		node {
			patchfunctions.log("Installation of apg-jadas-service-${target} starting on host ${host}","linuxServiceInstaller")
			def yumCmdOptions = "--disablerepo=* --enablerepo=apg-artifactory*"
			def yumCmd = "sudo yum clean all ${yumCmdOptions} && sudo yum -y install ${yumCmdOptions} apg-jadas-service-${target}"
			ssh(host, "echo \$( date +%Y/%m/%d-%H:%M:%S ) - executing with \$( whoami )@\$( hostname )")
			ssh(host, yumCmd)
		}
		
		patchfunctions.log("Installation of apg-jadas-service-${target} done!","linuxServiceInstaller")
	}
	return installer
}

def it21UiInstaller(target,host,buildVersion) {
	def installer = {
		node {
			patchfunctions.log("Installation of it21-ui starting for ${target} on host ${host}","it21UiInstaller")
			
			def group = "com.affichage.it21"
			def artifact = "it21gui-dist-zip"
			def artifactType = "zip"
	
			downloadGuiZipToBeInstalled(group,artifact,artifactType,buildVersion)
			def zipDist = "${artifact}-${buildVersion}.${artifactType}"
			
			def newFolderName = guiExtractedFolderName()
			
			// TODO JHE: Best to run all in one script ? ... not sure
			ssh(host, "sudo mkdir /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}")
			ssh(host, "sudo chgrp -R apg_install /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}")
			ssh(host, "sudo chmod 775 /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}")
			put(host, "./download/${zipDist}", "/etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}/${zipDist}")
			ssh(host, "sudo unzip /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}/${zipDist} -d /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}")
			put(host, "/etc/opt/apgops/config/${target}/it21-gui/AdGIS.properties", "/etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}/conf/AdGIS.properties")
			put(host, "/etc/opt/apgops/config/${target}/it21-gui/serviceurl.properties", "/etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}/conf/serviceurl.properties")
			ssh(host, "sudo chmod 755 /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName}/conf/*.*")
			ssh(host, "sudo mv /etc/opt/it21_ui_${target}/gettingExtracted_${newFolderName} /etc/opt/it21_ui_${target}/${newFolderName}")
			ssh(host, "sudo cd /etc/opt/it21_ui_${target}/ && rm -rf `ls -t | awk 'NR>2'`")
				
			patchfunctions.log("Installation of it21-ui done for ${target}","it21UiInstaller")
		}
	}
	return installer
}

def nopInstaller(serviceName) {
	def installer = {
		patchfunctions.log("No installer define for service ${serviceName}","nopInstaller")
	}
	return installer
}

def ssh(host,cmd) {
	patchfunctions.log("Running following command on ${host} via SSH: ${cmd}","ssh")
	def remote = getRemoteSSHConnection(host)
	sshCommand remote: remote, command: cmd
	patchfunctions.log("DONE - following command on ${host} via SSH: ${cmd}","ssh")
}

def put(host,src,dest) {
	patchfunctions.log("Putting ${src} on ${host} into ${dest}","put")
	def remote = getRemoteSSHConnection(host)
	sshPut remote: remote, from: src, into: dest
	patchfunctions.log("DONE - Putting ${src} on ${host} into ${dest}","put")
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
	
	node (env.WINDOWS_INSTALLER_OLDSTYLE_LABEL){
		// jenkins_pipeline_patch_install_oldstyle starts also the installation of Docker Services
		bat("cmd /c c:\\local\\software\\cm_winproc_root\\it21_extensions\\jenkins_pipeline_patch_install_oldstyle.bat ${patchConfig.patchNummer} ${patchConfig.currentTarget}")
	}
}

def installDbPatch(patchConfig,artifact,extension,host,type) {
	def server = initiateArtifactoryConnection()
	def patchDbFolderName = patchfunctions.getCoPatchDbFolderName(patchConfig)
	
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
		patchfunctions.log("Waiting 60 seconds before trying to rename the extracted ZIP.","installJadasGUI")
		sleep(time:60,unit:"SECONDS")
		renameExtractedGuiZip(extractedGuiPath,extractedFolderName)
		patchfunctions.log("GUI Folder correctly renamed.","installJadasGUI")
		copyCitrixBatchFile(extractedGuiPath,extractedFolderName)
		removeOldGuiFolder(extractedGuiPath)

		// Unmount the share drive
		powershell("net use ${extractedGuiPath} /delete")
	}
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
	// TODO JHE: -s option with patch to jenkins home folder, really needed? If needed, really what we want?
	def mvnCommand = "mvn dependency:copy -Dartifact=${groupId}:${artifactId}:${buildVersion}:${artifactType} -DoutputDirectory=./download -s /home/jenkins/.m2/settings.xml"
	patchfunctions.log("Downloading GUI-ZIP with following command: ${mvnCommand}","downloadGuiZipToBeInstalled")
	withMaven( maven: 'apache-maven-3.5.0') { sh "${mvnCommand}" }
	patchfunctions.log("GUI-ZIP correctly downloaded.","downloadGuiZipToBeInstalled")
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