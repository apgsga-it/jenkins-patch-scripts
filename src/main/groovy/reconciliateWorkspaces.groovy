#!/usr/bin/env groovy
def cli = new CliBuilder(usage: '-j|jenkins <directory>')
def outputWriter = new StringWriter()
cli.setWriter(new PrintWriter(outputWriter))
cli.with {
	h longOpt: 'help', 'Show usage information', required: false
	j longOpt: 'jenkins',args:1 , argName: 'directory', 'Jenkins installation directory', required: true
	u longOpt: 'update', 'If to run with updates', required: false
}
def opt = cli.parse(args)
if (!opt) {
	println outputWriter.toString()
	return 
}
if (opt.h)  {
	cli.usage() 
	return
}
// Validate if directory
def directory = new File(opt.j)
print directory
if (!directory.exists() | !directory.directory) {
	println "error: Directory ${opt.j} not valid: either not a directory or it doesn't exist"
	cli.usage()
	return
}
// Validate if Jenkins Installation
def jobsDir = new File(directory,"jobs") 
if (!jobsDir.exists() | !jobsDir.directory) {
	println "error: Does'nt seem to be a Jenkins installation, no jobs subdirectory"
	cli.usage()
	return
}
def workspacesDir = new File(directory,"workspace")
if (!workspacesDir.exists() | !workspacesDir.directory) {
	println "error: Does'nt seem to be a Jenkins installation, no workspace subdirectory"
	cli.usage()
	return
}
// Validate permissions
def dry = !opt.u 
println "Running with Updates : ${opt.u} "
if (!dry) {
	if (!workspacesDir.canWrite()) {
		println "error: No sufficient rights for workspace subdirectory"
		cli.usage()
		return 
	}
}
def cntInspected = 0
def cntDeleted = 0
println "Cleaning up workspaces in : ${workspacesDir.getPath()} "
workspacesDir.eachDir() { dir -> 
	println "Inspecting workspace Directory: ${dir.getName()}"
	cntInspected++
	def pos = dir.getName().indexOf("@")
	def jobName = pos < 0 ? dir.getName() : dir.getName().substring(0,pos)
	println "Resolved Jobname : ${jobName}"
	def jobDir = new File(jobsDir,jobName)
	if (jobDir.exists()) {
		println "++++++ corresponding Job Directory exists: ${jobDir.getPath()}"
	} else {
		println "------ No Job for: ${dir.getName()}"
		println "       Directory ${dir.getName()} can be deleted" 
		if (!dry) {
			if (dir.deleteDir()) {
				cntDeleted++
				println "       ${dir.getName()} has been deleted" 
			} else {
				println "       ${dir.getName()} has NOT been deleted, eventough we tried"
			}
		} else {
			println "       Running dry ${dir.getName()} not deleted"
		}
	}
}
println "Dirs inspected:  ${cntInspected} " 
println "Dirs deleted  :  ${cntDeleted}"

