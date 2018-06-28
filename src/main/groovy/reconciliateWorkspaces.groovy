#!/usr/bin/env groovy
def cli = new CliBuilder(usage: '-j|jenkins <directory>')
cli.with {
	h longOpt: 'help', 'Show usage information', required: false
	j longOpt: 'jenkins',args:1 , argName: 'directory', 'Jenkins installation directory', required: true
	u longOpt: 'update', 'If to run with updates', required: false
}
def opt = cli.parse(args)
if (!opt) {
	System.exit(1)
}
if (opt.h)  {
	cli.usage()
	System.exit(1)
}
// Validate if directory
def directory = new File(opt.j)
print directory
if (!directory.exists() | !directory.directory) {
	println "Directory ${opt.j} not valid: either not a directory or it doesn't exist"
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
def dry = !opt.u 
println "Running with Updates : ${opt.u}"
if (!dry) {
	if (!workspacesDir.canWrite()) {
		println "Not sufficient rights for workspace subdirectory"
		System.exit(1)
	}
}
println "Cleaning up workspaces in : ${workspacesDir.getPath()}"
workspacesDir.eachDir() { dir -> 
	println "Inspecting workspace Directory: ${dir.getName()}"
	def pos = dir.getName().indexOf("@")
	def jobName = pos < 0 ? dir.getName() : dir.getName().substring(0,pos)
	println "Resolved Jobname : ${jobName}"
	def jobDir = new File(jobsDir,jobName)
	if (jobDir.exists()) {
		println "++++++ Corresponding Job Directory exists: ${jobDir.getPath()}"
	} else {
		println "------ No Job for: ${dir.getName()}"
		println "Directory ${dir.getName()} can be deleted" 
		if (!dry) {
			if (dir.deleteDir()) {
				println "Directory ${dir.getName()} has been deleted" 
			} else {
				println "Directory ${dir.getName()} has NOT been deleted"
			}
		} else {
			println "Running dry ${dir.getName()} not deleted"
		}
	}
}

