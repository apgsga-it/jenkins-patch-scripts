library("patch-ad-functions-library")
def targetSystemMappingFile = libraryResource("TargetSystemMappings.json")
// TODO JHE: To be verified with UGE, which code should we use, new ones? 113 is definitely wrong, just as an example for 2 of my patches on CHEI212. Also, CHEI212 probably won't be in the list here
def targetCodeStatus = ["chei212":"113","chei211":"xxx","chti211":"yyy","chpi211":"zzz"]
def serviceInPatches = ""
def stashName = new Date().format("yyyyMMdd_HHmmssSSS")

echo "Pipeline is running for target ${TARGET}"
echo "Stash name : ${stashName}"

pipeline {
    agent any
    stages {
        stage("Get Patch JSON files and stash") {
            steps {
                // TODO JHE: That should work without specifying the full path, but seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf ${targetCodeStatus.get(TARGET)},${env.WORKSPACE}")
                stash name: stashName, includes: "*.json"
                sh("rm -f *.json")

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
                        patchADFunctions.assembleAndDeploy(TARGET,stashName,targetSystemMappingFile,serviceInPatches)
                    }
            }
        }

        stage("Cleaning Workspace") {
            // JHE: The stash can be preserved, but we don't have to keep the folder where stashed files have been extracted
            steps {
                sh("rm -rf ${stashName}")
            }
        }
    }
}