import hudson.*

def thr = Thread.currentThread()
// get current build
def build = thr?.executable
 
 
// get parameters
def parameters = build?.actions.find{ it instanceof ParametersAction }?.parameters
parameters.each {
   println "parameter ${it.name}:"
   println it.dump()
   println "-" * 80
}
 
 
// ... or if you want the parameter by name ...
def hardcoded_param = "FOOBAR"
def resolver = build.buildVariableResolver
def hardcoded_param_value = resolver.resolve(hardcoded_param)
 
 
println "param ${hardcoded_param} value : ${hardcoded_param_value}"
import jenkins.model.*
def dry = true
// First Delete Job in Patch Views
println "Deleting all Job for Patch Views"
["ProductivePatches", "Patches"].each { viewName ->
	println "Deleteing Jobs from ${viewName}"
	Jenkins.instance.getView(viewName).items.each { item ->
		println "About to delete $item.name"
		if (!dry) {
			item.delete()
			println "Deleted $item.name"
		} else {
			println "Did'nt delete anything, running dry"
		}
	}
}
println "Done."
println "Deleteing all Builds from remainging Jobs"
// Then Deleted all Builds for existing Jobs
def jobs = Jenkins.instance.getAllItems(hudson.model.AbstractProject.class).each {  job ->
	println "About to delete Builds for ${job.name}"
	job.getBuilds().each { build ->
		println "About to deleted Build ${build}"
		if (!dry) {
			build.delete()
			println "Deleted Build"
		} else  {
			println "Did'nt delete anything, running dry"
		}
	}
}
println "Deleteing all Builds from remaining Maven Jobs"
// Delete the missed Maven Jobs
jobs = Jenkins.instance.getAllItems(hudson.maven.MavenModuleSet.class).each {  job ->
	println "About to delete Builds for Maven Job ${job.name}"
	job.getBuilds().each { build ->
		println "About to deleted Build ${build}"
		if (!dry) {
			build.delete()
			println "Deleted Build"
		} else  {
			println "Did'nt delete anything, running dry"
		}
	}
}