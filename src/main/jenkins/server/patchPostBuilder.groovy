import hudson.model.*
import groovy.json.JsonSlurperClassic


def hardcoded_param = "jobName"
def resolver = build.buildVariableResolver
def hardcoded_param_value = resolver.resolve(hardcoded_param)


// Parameter
//def jobs = new JsonSlurperClassic().parseText(params.PARAMETER)


println("params :" + hardcoded_param_value)

//println "jobs = ${jobs}"

/*
def patchView = Hudson.instance.getView('Patches')
productivePatchView.doAddJobToView(downloadJob.name)
*/