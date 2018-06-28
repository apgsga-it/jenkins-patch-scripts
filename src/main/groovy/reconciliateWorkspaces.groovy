def cli = new CliBuilder(usage: '-j|jenkins <directory>')
cli.with {
	j longOpt: 'jenkins',args:1 , argName: 'directory', 'Jenkins installation directory', required: true
	u longOpt: 'update', 'If to run with updates'
}
def options = cli.parse(args)
if (options == null) {
	System.exit(1)
}
// Validate if directory
def directory = new File(options.j)
if (!directory.exists() | !directory.directory) {
	println "Directory ${options.l} not valid: either not a directory or it doesn't exist"
	System.exit(1)
}
// Validate if Jenkins Installation
def jobsDir = new File(directory,"jobs") 
if (!jobsDir.exists() | !jobsDir.directory) {
	println "Does'nt seem Jenkins installation, no jobs subdirectory"
	System.exit(1)
}
def workspacesDir = new File(directory,"workspace")
if (!workspacesDir.exists() | !workspacesDir.directory) {
	println "Does'nt seem Jenkins installation, no workspace subdirectory"
	System.exit(1)
}
// Validate permissions
def dry = !options.u 
println "Running with Updates : ${options.u}"
if (!dry) {
	if (!workspacesDir.canWrite()) {
		println "Not sufficient rights for workspace subdirectory"
		System.exit(1)
	}
}
println "Cleaning up workspaces in : ${workspacesDir.getPath()}"
workspacesDir.eachDir() { dir -> 
	println "Inspecting workspace Directory: ${dir.getName()}"
	def jobDir = new File(jobsDir,dir.getName())
	if (jobDir.exists()) {
		println "Corresponding Job Directory exists: ${jobDir.getPath()}"
	} else {
		println "No Job for: ${dir.getName()}"
	}
}

