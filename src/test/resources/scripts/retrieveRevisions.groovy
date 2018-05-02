import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

def targetSystemFile = new File("src/test/resources/json", "TargetSystemMappings.json")
def jsonSystemTargets = new JsonSlurper().parseText(targetSystemFile.text)
def targetSystemMap = [:]
jsonSystemTargets.targetSystems.each( { target -> targetSystemMap.put(target.name, new Expando(envName:target.name,target:target.target,targetTypeInd:target.typeInd))})
println targetSystemMap
def envName = "Informatiktest"
def targetBean = targetSystemMap.get(envName)
assert targetBean != null
println "Target Bean:" + targetBean
def revisionFileName = "src/test/resources/xxxx/Revisions.json"
def revisionFile = new File(revisionFileName)
def currentRevision = [P:1,T:1]
def lastRevision = [:]
def revisions = [lastRevisions:lastRevision, currentRevision:currentRevision]
if (revisionFile.exists()) {
	revisions = new JsonSlurper().parseText(revisionFile.text)
}
println "TypeInd: ${targetBean.targetTypeInd}"
def revision = revisions.currentRevision[targetBean.targetTypeInd]
println "Target name: ${targetBean.target}"
def currentLastRevision = revisions.lastRevisions.get(targetBean.target,'SNAPSHOT')
println revision
println currentLastRevision