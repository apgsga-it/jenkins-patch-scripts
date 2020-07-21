library("patch-ad-functions-library")
def targetSystemMappingFile = libraryResource("TargetSystemMappings.json")
// TODO JHE: To be verified with UGE, which code should we use, new ones? 113 is definitely wrong, just as an example. Also, CHEI212 probably won't be in the list here
def targetCodeStatus = ["chei212":"113","chei211":"xxx","chti211":"yyy","chpi211":"zzz"]
pipeline {
    agent any
    environment {
        dirName = new Date().format("yyyyMMdd_HHmmssSSS")
        serviceInPatches = ""
    }
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
                sh("mkdir ${env.dirName}")
                echo "Path of directory containing JSON Patch file for current pipeline execution: ${env.WORKSPACE}/${dirName}"
            }
        }

        stage("Getting list of services within Patche(s)") {
            steps {
                // TODO JHE: That should work without specifying the full path, but seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf ${targetCodeStatus.get(TARGET)},${env.dirName}")
                stash name: "${env.dirName}_stashed", includes: "${env.dirName}/*"
                script {
                    serviceInPatches = patchADFunctions.servicesInPatches("${env.WORKSPACE}/${dirName}")
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

        stage("Cleaning workspace"){
            steps {
                sh("rm -rf ${dirName}")
            }
        }
    }
}