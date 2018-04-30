library 'patch-global-functions'

// Test parameter
def target = "CHEI212"
def zip = "it21gui-dist-zip-9.0.6.ADMIN-UIMIG-20180404.063723-15.zip"

stage("Testing installation of GUI on specific node") {
	node("Apg_jdv_CHEI212") {
		
		// Will probably be removed, but for now we need to initiate the connection on \\gui-chei212.apgsga.ch ...
		powershell("invoke-expression -Command \"C:\\Software\\initAndClean\\init_install_${target}_it21gui.ps1\"")
		
		
		def artifactoryServer = patchfunctions.initiateArtifactoryConnection()
		patchfunctions.downloadGuiZipToBeInstalled(artifactoryServer,zip)
		
		
		def currentDateAndTime = new Date().format('yyyyMMddHHmmss')
		def extractedFolderName = "java_gui_${currentDateAndTime}"
		
		patchfunctions.extractZip(zip,target,extractedFolderName)
		patchfunctions.renameExtractedZip(target,extractedFolderName)
		patchfunctions.copyOpsResources(target,extractedFolderName)
		
		
		// Will probably be removed, but we call a script to reset the connection which was initiated on \\gui-chei212.apgsga.ch
		powershell("invoke-expression -Command \"C:\\Software\\initAndClean\\clean_install_${target}_it21gui.ps1\"")
		
	}
}