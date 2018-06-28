import com.cloudbees.hudson.plugins.folder.Folder
import hudson.FilePath
import jenkins.model.Jenkins

def dry = true

def boolean isFolder(String name) {
    def item = Jenkins.instance.getItemByFullName(name)
    return item instanceof Folder
}

def deleteUnusedWorkspace(FilePath root, String path) {
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
    println "Processing $node.displayName"
    def workspaceRoot = node.rootPath.child("workspace");
    deleteUnusedWorkspace(workspaceRoot, "")
}