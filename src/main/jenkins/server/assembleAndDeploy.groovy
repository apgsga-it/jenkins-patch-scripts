library("patch-ad-functions-library")
def targetSystemMappingFile = libraryResource("TargetSystemMappings.json")
// TODO JHE: To be verified with UGE, which code should we use, new ones? 113 is definitely wrong, just as an example. Also, CHEI212 probably won't be in the list here
def targetCodeStatus = ["chei212":"113","chei211":"xxx","chti211":"yyy","chpi211":"zzz"]
def dirName = new Date().format("yyyyMMdd_HHmmssSSS")
def serviceInPatches = ""
def stashName = "${dirName}_stashed"

echo "Pipeline is running for target ${TARGET}"
echo "Temporary path of directory containing JSON Patch files which will be stashed: ${env.WORKSPACE}/${dirName}"
echo "Stash name : ${stashName}"

pipeline {
    agent any
    stages {
        stage("Get Patch JSON and stash") {
            steps {
                sh("mkdir ${dirName}")
                // TODO JHE: That should work without specifying the full path, but seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf ${targetCodeStatus.get(TARGET)},${dirName}")
                stash name: stashName, includes: "${dirName}/*"
                sh("rm -rf ${dirName}")

            }
        }

        stage("Get list of services within Patche(s)") {
            steps {
                script {
                    serviceInPatches = patchADFunctions.servicesInPatches(stashName)
                }
            }
        }

        stage("Getting Pkg projects from CVS") {
            steps {
                script {
                    patchADFunctions.coPackageProjects(serviceInPatches)
                }
            }
        }

        stage("Assembling and deploying projects") {
            steps {
                script {
                        patchADFunctions.assembleAndDeploy(TARGET,"${env.WORKSPACE}/${dirName}",targetSystemMappingFile,serviceInPatches)
                    }
            }
        }
    }
}