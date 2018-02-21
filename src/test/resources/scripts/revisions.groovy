import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

def revisionFileName = "src/test/resources/json/revisions.json"
def revisionFile = new File(revisionFileName)
if (!revisionFile.exists()) {
	def currentRevision = [P:1,T:1]
	def lastRevision = [:]
	def revisions = [lastRevisions:lastRevision, currentRevision:currentRevision]
	revisionFile.write(new JsonBuilder(revisions).toPrettyString())
}
def revision = new JsonSlurper().parseText(revisionFile.text)
println "${revision}"
println "${revision.currentRevision.P}"
println "${revision.currentRevision.T}"
def lastRevision = revision.currentRevision.T
revision.currentRevision.T++
println "${revision.currentRevision.T}"
println revision.lastRevisions.get('CHEI212','SNAPSHOT')
revision.lastRevisions['CHEI212'] = "${lastRevision}"
new File(revisionFileName).write(new JsonBuilder(revision).toPrettyString())