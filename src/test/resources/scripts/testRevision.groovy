import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

revisionFileName = "src/test/resources/json/Revisions.json"

// JHE (25.05.2018): method to be copied within patchfunctions.groovy. Don't forget to uncomment the "revisionFileName" part
def retrieveRevisions(patchConfig) {
	//def revisionFileName = "${env.JENKINS_HOME}/userContent/PatchPipeline/data/Revisions.json"
	def revisionFile = new File(revisionFileName)
	def currentRevision = [P:1,T:10000]
	def lastRevision = [:]
	def revisions = [lastRevisions:lastRevision, currentRevision:currentRevision]
	if (revisionFile.exists()) {
		revisions = new JsonSlurper().parseText(revisionFile.text)
	}
	
	if(patchConfig.targetInd.equals("P")) {
		patchConfig.revision = revisions.currentRevision[patchConfig.targetInd]
	}
	else {
		if(revisions.lastRevisions.get(patchConfig.installationTarget) == null) {
			patchConfig.revision = revisions.currentRevision[patchConfig.targetInd]
		}
		else {
			patchConfig.revision = revisions.lastRevisions.get(patchConfig.installationTarget) + 1
		}
	}

	patchConfig.lastRevision = revisions.lastRevisions.get(patchConfig.installationTarget,'SNAPSHOT')
}

// JHE (25.05.2018): method to be copied within patchfunctions.groovy. Don't forget to uncomment the "revisionFileName" part
def saveRevisions(patchConfig) {
	//def revisionFileName = "${env.JENKINS_HOME}/userContent/PatchPipeline/data/Revisions.json"
	def revisionFile = new File(revisionFileName)
	def currentRevision = [P:1,T:10000]
	def lastRevision = [:]
	def revisions = [lastRevisions:lastRevision, currentRevision:currentRevision]
	if (revisionFile.exists()) {
		revisions = new JsonSlurper().parseText(revisionFile.text)
	}
	if(patchConfig.targetInd.equals("P")) {
		revisions.currentRevision[patchConfig.targetInd]++
	}
	else {
		// We increase it only when saving a new Target
		if(revisions.lastRevisions.get(patchConfig.installationTarget) == null) {
			revisions.currentRevision[patchConfig.targetInd] = revisions.currentRevision[patchConfig.targetInd] + 10000
		}
	}
	revisions.lastRevisions[patchConfig.installationTarget] = patchConfig.revision
	new File(revisionFileName).write(new JsonBuilder(revisions).toPrettyString())

}

def assertValuesWithinRevisionsFile(chei212Last,chei211Last,chpi211Last,chti211Last,currentT,currentP) {
	
	def revisionFile = new File(revisionFileName)
	def revisions = new JsonSlurper().parseText(revisionFile.text)
	println("ASSERT OF REVISION FILE STARTEING FOR:")
	println(revisions)
	assert (revisions.lastRevisions["CHEI212"]) == chei212Last : "Last revision for CHEI212 is wrong, was: " + revisions.lastRevisions["CHEI212"]
	assert (revisions.lastRevisions["CHEI211"]) == chei211Last : "Last revision for CHEI211 is wrong, was: " + revisions.lastRevisions["CHEI211"]
	assert (revisions.lastRevisions["CHPI211"]) == chpi211Last : "Last revision for CHPI211 is wrong, was: " + revisions.lastRevisions["CHPI211"]
	assert (revisions.lastRevisions["CHTI211"]) == chti211Last : "Last revision for CHTI211 is wrong, was: " + revisions.lastRevisions["CHTI211"]
 	assert (revisions.currentRevision["T"] == currentT) : "Current T is wrong, was: " + revisions.currentRevision["T"]
	assert (revisions.currentRevision["P"] == currentP) : "Current P is wrong, was: " + revisions.currentRevision["P"]
	println("ASSERT OF REVISION FILE DONE.")
}


new File(revisionFileName).delete()

println "*****************"
println "Starting test ..."
println "*****************"
println ""

// CHEI212

def patchConfigCHEI212 = [:]
patchConfigCHEI212.targetInd = "T"
patchConfigCHEI212.installationTarget = "CHEI212"
println patchConfigCHEI212
retrieveRevisions(patchConfigCHEI212)
println patchConfigCHEI212
assert (patchConfigCHEI212.revision == 10000) : "revision was ${patchConfigCHEI212.revision}"
assert (patchConfigCHEI212.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHEI212.lastRevision}" 
assert (patchConfigCHEI212.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212.installationTarget}"

saveRevisions(patchConfigCHEI212)
println patchConfigCHEI212
assert (patchConfigCHEI212.revision == 10000) : "revision was ${patchConfigCHEI212.revision}"
assert (patchConfigCHEI212.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHEI212.lastRevision}"
assert (patchConfigCHEI212.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212.installationTarget}"

assertValuesWithinRevisionsFile(10000, null, null, null, 20000, 1)


retrieveRevisions(patchConfigCHEI212)
println patchConfigCHEI212
assert (patchConfigCHEI212.revision == 10001) : "revision was ${patchConfigCHEI212.revision}"
assert (patchConfigCHEI212.lastRevision == 10000) : "lastRevision was ${patchConfigCHEI212.lastRevision}"
assert (patchConfigCHEI212.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212.installationTarget}"


saveRevisions(patchConfigCHEI212)
println patchConfigCHEI212
assert (patchConfigCHEI212.revision == 10001) : "revision was ${patchConfigCHEI212.revision}"
assert (patchConfigCHEI212.lastRevision == 10000) : "lastRevision was ${patchConfigCHEI212.lastRevision}"
assert (patchConfigCHEI212.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212.installationTarget}"

assertValuesWithinRevisionsFile(10001, null, null, null, 20000, 1)

retrieveRevisions(patchConfigCHEI212)
println patchConfigCHEI212
assert (patchConfigCHEI212.revision == 10002) : "revision was ${patchConfigCHEI212.revision}"
assert (patchConfigCHEI212.lastRevision == 10001) : "lastRevision was ${patchConfigCHEI212.lastRevision}"
assert (patchConfigCHEI212.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212.installationTarget}"

saveRevisions(patchConfigCHEI212)
println patchConfigCHEI212
assert (patchConfigCHEI212.revision == 10002) : "revision was ${patchConfigCHEI212.revision}"
assert (patchConfigCHEI212.lastRevision == 10001) : "lastRevision was ${patchConfigCHEI212.lastRevision}"
assert (patchConfigCHEI212.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212.installationTarget}"

assertValuesWithinRevisionsFile(10002, null, null, null, 20000, 1)

// END CHEI212

// CHEI211

def patchConfigCHEI211 = [:]
patchConfigCHEI211.targetInd = "T"
patchConfigCHEI211.installationTarget = "CHEI211"
println patchConfigCHEI211
retrieveRevisions(patchConfigCHEI211)
println patchConfigCHEI211
assert (patchConfigCHEI211.revision == 20000) : "revision was ${patchConfigCHEI211.revision}"
assert (patchConfigCHEI211.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHEI211.lastRevision}"
assert (patchConfigCHEI211.installationTarget.equals("CHEI211")) : "installationTarget was ${patchConfigCHEI211.installationTarget}"

saveRevisions(patchConfigCHEI211)
println patchConfigCHEI211
assert (patchConfigCHEI211.revision == 20000) : "revision was ${patchConfigCHEI211.revision}"
assert (patchConfigCHEI211.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHEI211.lastRevision}"
assert (patchConfigCHEI211.installationTarget.equals("CHEI211")) : "installationTarget was ${patchConfigCHEI211.installationTarget}"

assertValuesWithinRevisionsFile(10002, 20000, null, null, 30000, 1)

retrieveRevisions(patchConfigCHEI211)
println patchConfigCHEI211
assert (patchConfigCHEI211.revision == 20001) : "revision was ${patchConfigCHEI211.revision}"
assert (patchConfigCHEI211.lastRevision == 20000) : "lastRevision was ${patchConfigCHEI211.lastRevision}"
assert (patchConfigCHEI211.installationTarget.equals("CHEI211")) : "installationTarget was ${patchConfigCHEI211.installationTarget}"

saveRevisions(patchConfigCHEI211)
println patchConfigCHEI211
assert (patchConfigCHEI211.revision == 20001) : "revision was ${patchConfigCHEI211.revision}"
assert (patchConfigCHEI211.lastRevision == 20000) : "lastRevision was ${patchConfigCHEI211.lastRevision}"
assert (patchConfigCHEI211.installationTarget.equals("CHEI211")) : "installationTarget was ${patchConfigCHEI211.installationTarget}"

assertValuesWithinRevisionsFile(10002, 20001, null, null, 30000, 1)

retrieveRevisions(patchConfigCHEI211)
println patchConfigCHEI211
assert (patchConfigCHEI211.revision == 20002) : "revision was ${patchConfigCHEI211.revision}"
assert (patchConfigCHEI211.lastRevision == 20001) : "lastRevision was ${patchConfigCHEI211.lastRevision}"
assert (patchConfigCHEI211.installationTarget.equals("CHEI211")) : "installationTarget was ${patchConfigCHEI211.installationTarget}"

saveRevisions(patchConfigCHEI211)
println patchConfigCHEI211
assert (patchConfigCHEI211.revision == 20002) : "revision was ${patchConfigCHEI211.revision}"
assert (patchConfigCHEI211.lastRevision == 20001) : "lastRevision was ${patchConfigCHEI211.lastRevision}"
assert (patchConfigCHEI211.installationTarget.equals("CHEI211")) : "installationTarget was ${patchConfigCHEI211.installationTarget}"

assertValuesWithinRevisionsFile(10002, 20002, null, null, 30000, 1)

// END CHEI211

// AGAIN CHEI212, THIS TIME WITH AN ALREADY EXISTING REVISION FILE

def patchConfigCHEI212_2 = [:]
patchConfigCHEI212_2.targetInd = "T"
patchConfigCHEI212_2.installationTarget = "CHEI212"
println patchConfigCHEI212_2
retrieveRevisions(patchConfigCHEI212_2)
println patchConfigCHEI212_2
assert (patchConfigCHEI212_2.revision == 10003) : "revision was ${patchConfigCHEI212_2.revision}"
assert (patchConfigCHEI212_2.lastRevision == 10002) : "lastRevision was ${patchConfigCHEI212_2.lastRevision}"
assert (patchConfigCHEI212_2.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212_2.installationTarget}"

saveRevisions(patchConfigCHEI212_2)
println patchConfigCHEI212_2
assert (patchConfigCHEI212_2.revision == 10003) : "revision was ${patchConfigCHEI212_2.revision}"
assert (patchConfigCHEI212_2.lastRevision == 10002) : "lastRevision was ${patchConfigCHEI212_2.lastRevision}"
assert (patchConfigCHEI212_2.installationTarget.equals("CHEI212")) : "installationTarget was ${patchConfigCHEI212_2.installationTarget}"

assertValuesWithinRevisionsFile(10003, 20002, null, null, 30000, 1)

// END AGAIN CHEI212

// CHPI211

def patchConfigCHPI211 = [:]
patchConfigCHPI211.targetInd = "P"
patchConfigCHPI211.installationTarget = "CHPI211"
println patchConfigCHPI211
retrieveRevisions(patchConfigCHPI211)
println patchConfigCHPI211
assert (patchConfigCHPI211.revision == 1) : "revision was ${patchConfigCHPI211.revision}"
assert (patchConfigCHPI211.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHPI211.lastRevision}"
assert (patchConfigCHPI211.installationTarget.equals("CHPI211")) : "installationTarget was ${patchConfigCHPI211.installationTarget}"

saveRevisions(patchConfigCHPI211)
println patchConfigCHPI211
assert (patchConfigCHPI211.revision == 1) : "revision was ${patchConfigCHPI211.revision}"
assert (patchConfigCHPI211.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHPI211.lastRevision}"
assert (patchConfigCHPI211.installationTarget.equals("CHPI211")) : "installationTarget was ${patchConfigCHPI211.installationTarget}"

assertValuesWithinRevisionsFile(10003, 20002, 1, null, 30000, 2)

retrieveRevisions(patchConfigCHPI211)
println patchConfigCHPI211
assert (patchConfigCHPI211.revision == 2) : "revision was ${patchConfigCHPI211.revision}"
assert (patchConfigCHPI211.lastRevision == 1) : "lastRevision was ${patchConfigCHPI211.lastRevision}"
assert (patchConfigCHPI211.installationTarget.equals("CHPI211")) : "installationTarget was ${patchConfigCHPI211.installationTarget}"

saveRevisions(patchConfigCHPI211)
println patchConfigCHPI211
assert (patchConfigCHPI211.revision == 2) : "revision was ${patchConfigCHPI211.revision}"
assert (patchConfigCHPI211.lastRevision == 1) : "lastRevision was ${patchConfigCHPI211.lastRevision}"
assert (patchConfigCHPI211.installationTarget.equals("CHPI211")) : "installationTarget was ${patchConfigCHPI211.installationTarget}"

assertValuesWithinRevisionsFile(10003, 20002, 2, null, 30000, 3)

// END CHPI211

// CHTI211

def patchConfigCHTI211 = [:]
patchConfigCHTI211.targetInd = "T"
patchConfigCHTI211.installationTarget = "CHTI211"
retrieveRevisions(patchConfigCHTI211)
println patchConfigCHTI211
assert (patchConfigCHTI211.revision == 30000) : "revision was ${patchConfigCHTI211.revision}"
assert (patchConfigCHTI211.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHTI211.lastRevision}"
assert (patchConfigCHTI211.installationTarget.equals("CHTI211")) : "installationTarget was ${patchConfigCHTI211.installationTarget}"

saveRevisions(patchConfigCHTI211)
println patchConfigCHTI211
assert (patchConfigCHTI211.revision == 30000) : "revision was ${patchConfigCHTI211.revision}"
assert (patchConfigCHTI211.lastRevision == "SNAPSHOT") : "lastRevision was ${patchConfigCHTI211.lastRevision}"
assert (patchConfigCHTI211.installationTarget.equals("CHTI211")) : "installationTarget was ${patchConfigCHTI211.installationTarget}"

assertValuesWithinRevisionsFile(10003, 20002, 2, 30000, 40000, 3)

retrieveRevisions(patchConfigCHTI211)
println patchConfigCHTI211
assert (patchConfigCHTI211.revision == 30001) : "revision was ${patchConfigCHTI211.revision}"
assert (patchConfigCHTI211.lastRevision == 30000) : "lastRevision was ${patchConfigCHTI211.lastRevision}"
assert (patchConfigCHTI211.installationTarget.equals("CHTI211")) : "installationTarget was ${patchConfigCHTI211.installationTarget}"

saveRevisions(patchConfigCHTI211)
println patchConfigCHTI211
assert (patchConfigCHTI211.revision == 30001) : "revision was ${patchConfigCHTI211.revision}"
assert (patchConfigCHTI211.lastRevision == 30000) : "lastRevision was ${patchConfigCHTI211.lastRevision}"
assert (patchConfigCHTI211.installationTarget.equals("CHTI211")) : "installationTarget was ${patchConfigCHTI211.installationTarget}"

assertValuesWithinRevisionsFile(10003, 20002, 2, 30001, 40000, 3)

// END CHTI211

println ""
println "***************************"
println "Test ended without failure."
println "***************************"