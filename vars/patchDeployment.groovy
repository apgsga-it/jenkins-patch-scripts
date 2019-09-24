#!groovy
library 'patch-global-functions'

def installDeploymentArtifacts(patchConfig) {
	
	def targetSystemMappingJson = patchfunctions.getTargetSystemMappingJson()
	
	lock("${patchConfig.serviceName}${patchConfig.currentTarget}Install") {
		parallel 'ui-client-deployment': {
			if(patchConfig.installJadasAndGui && !isLightInstallation(patchConfig.currentTarget,targetSystemMappingJson)) {
				node {
					installJadasGUI(patchConfig)
				}
			}
		}, 'ui-server-deployment': {
			if(patchConfig.installJadasAndGui && !isLightInstallation(patchConfig.currentTarget,targetSystemMappingJson)) {
				echo "patchConfig.targetBean = ${patchConfig.targetBean}"
				def installationNodeLabel = patchfunctions.serviceInstallationNodeLabel(patchConfig.targetBean,"jadas")
				echo "Installation of jadas Service will be done on Node : ${installationNodeLabel}"
				node (installationNodeLabel){
					echo "Installation of apg-jadas-service-${patchConfig.currentTarget} starting ..."
					def yumCmdOptions = "--disablerepo=* --enablerepo=apg-artifactory*"
					def yumCmd = "sudo yum clean all ${yumCmdOptions} && sudo yum -y install ${yumCmdOptions} apg-jadas-service-${patchConfig.currentTarget}"
					sh "echo \$( date +%Y/%m/%d-%H:%M:%S ) - executing with \$( whoami )@\$( hostname )"
					sh "${yumCmd}"
					echo "Installation of apg-jadas-service-${patchConfig.currentTarget} done!"
				}
			}
		}, 'db-deployment': {
			node {
				installDbPatch(patchConfig,patchfunctions.getCoPatchDbFolderName(patchConfig),"zip",getTargetHost("it21-db",patchConfig.currentTarget,targetSystemMappingJson),getTargetType("it21-db",patchConfig.currentTarget,targetSystemMappingJson))
			}
		}
	}
}

def getTargetHost(service,target,targetSystemMappingJson) {
	// JHE (06.09.2019): By default, for hosts not listed under targetInstances, we consider the host same as target
	//					 This will be improved with ARCH-92, when all host will have additional services information
	//					 For now, this is used only at the time we install DB-Module
	//					 Consider moving the function to "patchfunctions" if the method gets called from other place(s) than installation steps
	def targetInstance = patchfunctions.getTargetInstance(target,targetSystemMappingJson)
	if(targetInstance != null) {
		def s = targetInstance.services.find{it.name == service}
		assert s != null : "${target} is configured as a targetInstance, but service ${service} has not been configured."
		assert s.host != null : "Host has not been configured for target ${target}"
		return s.host
	}
	println "no host configured for ${target} ... host=${target}"
	return target
}

def getTargetType(service,target,targetSystemMappingJson) {
	// JHE (06.09.2019): By default, for host not listed under targetInstances, we consider the target type as default with "oracle-db"
	//					 This will be improved with ARCH-92, when all host will have additional services information
	//					 For now, this is used only at the time we install DB-Module
	//					 Consider moving the function to "patchfunctions" if the method gets called from other place(s) than installation steps
	def targetInstance = patchfunctions.getTargetInstance(target,targetSystemMappingJson)
	if(targetInstance != null) {
		def s = targetInstance.services.find{it.name == service}
		assert s != null : "${target} is configured as a targetInstance, but service ${service} has not been configured."
		assert s.type != null : "Type of service has not been configured for target ${target}"
		return s.type
	}
	println "no service type configured for ${target} ... serviceType=oracle-db"
	return "oracle-db"
}

// JHE (06.09.2019): ARCH-90. For now, only DB Modules can be installed on Light-Instances. Therefore, we need to know if the target is a Light, in order to determine if we have to start the Jadas installation.
//				   : The method assumes that the service type contains "light". This should be done only temporarily until Jadas will be installed on Light as well.
//				   : If the need to determine if a target is a Light still remains, we might want to find a better than relying on a name which should contain a specific string...
def isLightInstallation(target,targetSystemMappingJson) {
	def isLight = false
	targetSystemMappingJson.targetInstances.each ({ targetInstance ->
		if(targetInstance.name == target) {
			targetInstance.services.each ({ service ->
				isLight = service.name == "it21-db" && service.type.contains("light")
			})
		}
	})
	println "is ${target} a Light-Instance: ${isLight}"
	isLight
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
		
		extractGuiZip(zip,extractedGuiPath,extractedFolderName)
		println "Waiting 60 seconds before trying to rename the extracted ZIP."
		sleep(time:60,unit:"SECONDS")
		renameExtractedGuiZip(extractedGuiPath,extractedFolderName)
		println "ZIP file correctly renamed."
		copyGuiOpsResources(patchConfig,extractedGuiPath,extractedFolderName)
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
	def mvnCommand = "mvn dependency:copy -Dartifact=${groupId}:${artifactId}:${buildVersion}:${artifactType} -DoutputDirectory=./download"
	echo "Downloading GUI-ZIP with following command: ${mvnCommand}"
	withMaven( maven: 'apache-maven-3.5.0', mavenSettingsConfig: 'C:\\local\\software\\maven\\settings.xml') { sh "${mvnCommand}" }
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
		fileOperations ([fileCopyOperation(flattenFiles: true, excludes: '', includes: '*.properties', targetLocation: "${extractedGuiPath}\\${extractedFolderName}\\conf")])
	}
}