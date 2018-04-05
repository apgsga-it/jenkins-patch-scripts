library 'patch-global-functions'

stage("Testing installation of GUI on specific node") {
	node("Apg_jdv_CHEI212") {
		echo "Starting a testing installation of GUI on specific node ..."
		
		echo "Trying to call a funtion from patchFunctions.groovy..."
		patchfunctions.getGuiZipToBeInstalled()
		
		echo "Testing installation of GUI on specific node done!"
	}
}