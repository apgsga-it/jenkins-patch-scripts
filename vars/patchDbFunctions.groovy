#!groovy
library 'patch-global-functions'
library 'patch-deployment-functions'

def dbAssemble(patchConfig) {
	def PatchDbFolderName = getCoPatchDbFolderName(patchConfig)
	fileOperations ([folderCreateOperation(folderPath: "${PatchDbFolderName}\\config")])
	// Done in order for the config folder to be taken into account when we create the ZIP...
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\config\\dummy.txt", fileContent: "")])
	def cmPropertiesContent = "config_name:${PatchDbFolderName}\r\npatch_name:${PatchDbFolderName}\r\ntag_name:${PatchDbFolderName}"
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\cm_properties.txt", fileContent: cmPropertiesContent)])
	def configInfoContent = "config_name:${PatchDbFolderName}"
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\config_info.txt", fileContent: configInfoContent)])
	
	def installPatchContent = "@echo off\r\n"
	// TODO JHE: 0900C info doesn't exist at the moment witin patchConfig... also datetime ... do we have it somewhere?
	installPatchContent += "@echo *** Installation von Patch 0900C_${patchConfig.patchNummer} [Build von TODO get YYYY/MM/dd-HH:mm:ss]\r\n"
	installPatchContent += "set /p v_params=Geben Sie die Zielumgebung ein: \r\n"
	installPatchContent += "pushd %~dp0 \r\n\r\n"
	installPatchContent += "cmd /c \\\\cm-linux.apgsga.ch\\cm_ui\\it21_patch.bat %v_params%\r\n"
	installPatchContent += "popd"
	fileOperations ([fileCreateOperation(fileName: "${PatchDbFolderName}\\install_patch.bat", fileContent: installPatchContent)])
	
	publishDbAssemble(patchConfig)
}

def publishDbAssemble(patchConfig) {
	def server = patchDbFunctions.initiateArtifactoryConnection()
	def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
	def zipName = "${patchDbFolderName}.zip"
	fileOperations ([fileDeleteOperation(includes: zipName)])
	zip zipFile: zipName, glob: "${patchDbFolderName}/**"
	
		
	// TODO JHE: Target should better be a subfolder within releases ... like "db"
	def uploadSpec = """{
		"files": [
		{
			"pattern": "*.zip",
			"target": "releases/"
		  }
		]
	}"""
	server.upload(uploadSpec)
}

def getCoPatchDbFolderName(patchConfig) {
	return patchConfig.dbPatchBranch.replace("Patch", "test")
}

def coDbModules(patchConfig) {
	def dbObjects = patchConfig.dbObjectsAsVcsPath
	echo "Following DB Objects will be checked out : ${dbObjects}"
	def patchDbFolderName = getCoPatchDbFolderName(patchConfig)
	fileOperations ([folderDeleteOperation(folderPath: "${patchDbFolderName}")])
	fileOperations ([folderCreateOperation(folderPath: "${patchDbFolderName}")])
	dir(patchDbFolderName) {
		dbObjects.each{ dbo ->
			patchfunctions.coFromBranchCvs(patchConfig,dbo,"db")
		}
	}
}
