library("patch-ad-functions-library")
def targetSystemMappingFile = libraryResource("TargetSystemMappings.json")
pipeline {
    agent any
    environment {
        dirName = new Date().format("yyyyMMdd_HHmmssSSS")
    }
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
                sh("mkdir ${env.dirName}")
                echo "Path of directory containing JSON Patch file for current pipeline execution: ${env.WORKSPACE}/${env.dirName}"
            }
        }

        stage("Stashing JSON Patch files") {
            steps {
                // JHE: Seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                // TODO JHE: 113 = Informatiktestlieferung Bearbeitung , will probably be retrieved from TargetSystemMapping. Or could also be a Pipeline Job parameter
                // TODO JHE: To be verified with UGE
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf 113,${env.dirName}")
                stash name: "${env.dirName}_stashed", includes: "${env.dirName}/*"
            }
        }

        stage("Getting Pkg projects from CVS") {
            steps {
                script {
                    def serviceNames = patchADFunctions.servicesInPatches("${env.WORKSPACE}/${env.dirName}")
                    patchADFunctions.coPackageProjects(serviceNames)
                }
            }
        }

        stage("Assembling and deploying projects") {
            steps {
                script {
                        patchADFunctions.assembleAndDeploy(TARGET,"${env.WORKSPACE}/${env.dirName}",targetSystemMappingFile)
                    }
            }
        }
    }
}