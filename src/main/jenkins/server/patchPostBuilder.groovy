import hudson.model.*

def jobName_param = "jobName"
def downloadjobName_param = "downloadJobName"
def resolver = build.buildVariableResolver
def jobName_param_value = resolver.resolve(jobName_param)
def downloadJobName_param_value = resolver.resolve(downloadjobName_param)


// Parameter
//def jobs = new JsonSlurperClassic().parseText(params.PARAMETER)


println "jobName : ${jobName}"
println "downloadJobName : ${downloadJobName}"

//println "jobs = ${jobs}"

/*
def patchView = Hudson.instance.getView('Patches')
productivePatchView.doAddJobToView(downloadJob.name)
*/