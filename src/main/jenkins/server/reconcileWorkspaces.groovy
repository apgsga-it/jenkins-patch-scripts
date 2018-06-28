import com.cloudbees.hudson.plugins.folder.*
import hudson.*
import jenkins.model.*


def boolean isFolder(name) {
    def item = Jenkins.instance.getItemByFullName(name)
    return item instanceof Folder
}

def deleteUnusedWorkspace(root, path, dry) {
    root.list().each { child ->
        String fullName = path + child.name
        if (isFolder(fullName)) {
            deleteUnusedWorkspace(root.child(child.name), "$fullName/")
        } else {
            if (Jenkins.instance.getItemByFullName(fullName) == null) {
                println "Intending to Delete: $fullName "
				if (!dry) {
					child.deleteRecursive()
					println "Deleted: $fullName "
				} else {
					println "Skipped"
				}
					 
            }
        }
    }
}

for (node in Jenkins.instance.nodes) {
	def dry = true
    println "Processing $node.displayName"
    def workspaceRoot = node.rootPath.child("workspace");
    deleteUnusedWorkspace(workspaceRoot, "", dry)
}