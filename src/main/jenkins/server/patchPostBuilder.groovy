import hudson.model.*
import groovy.json.JsonSlurperClassic

properties([
	parameters([
		stringParam(
		defaultValue: "",
		description: 'Parameter',
		name: 'PARAMETER'
		)
	])
])

// Parameter
//def jobs = new JsonSlurperClassic().parseText(params.PARAMETER)


println("params :" + params)

//println "jobs = ${jobs}"

/*
def patchView = Hudson.instance.getView('Patches')
productivePatchView.doAddJobToView(downloadJob.name)
*/