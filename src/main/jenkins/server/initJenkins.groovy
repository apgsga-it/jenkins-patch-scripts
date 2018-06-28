import hudson.*
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
def jobs = Jenkins.instance.getAllItems(AbstractProject.class).each {  job ->
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