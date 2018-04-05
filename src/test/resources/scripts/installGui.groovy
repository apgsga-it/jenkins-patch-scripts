library 'patch-global-functions'

// Test parameter
def target = "CHEI212"
def zip = "it21gui-dist-zip-9.0.6.ADMIN-UIMIG-20180404.063723-15.zip"

stage("Testing installation of GUI on specific node") {
	node("Apg_jdv_CHEI212") {
		
		// Will probably be removed, but for now we need to initiate the connection on \\gui-chei212.apgsga.ch ...
		powershell("invoke-expression -Command \"C:\\Software\\initAndClean\\init_install_${target}_it21gui.ps1\"")
		
		echo "Starting a testing installation of GUI on specific node ..."
		
		echo "Trying to call a funtion from patchFunctions.groovy..."
		
		def artifactoryServer = patchFunctions.initiateArtifactoryConnection()
		patchfunctions.downloadGuiZipToBeInstalled(artifactoryServer,"it21gui-dist-zip-9.0.6.ADMIN-UIMIG-20180404.063723-15.zip")
		
		echo "Testing installation of GUI on specific node done!"
		
		
		// Will probably be removed, but we call a script to reset the connection which was initiated on \\gui-chei212.apgsga.ch
		powershell("invoke-expression -Command \"C:\\Software\\initAndClean\\clean_install_${target}_it21gui.ps1\"")
		
	}
}