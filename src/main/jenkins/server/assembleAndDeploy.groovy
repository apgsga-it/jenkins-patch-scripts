library("patch-ad-functions-library")

pipeline {
    agent any
    environment {
        dirName = new Date().format("yyyyMMdd_HHmmssSSS")
    }
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
                echo "Name of directory containing JSON Patch file: ${env.dirName}"
                sh("mkdir ${env.dirName}")
            }
        }

        stage("Getting JSON Patch files") {
            steps {
                // JHE: Seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                // TODO JHE: 113 = Informatiktestlieferung Bearbeitung , will probably be retrieved from TargetSystemMapping. Or could also be a Pipeline Job parameter
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf 113,${env.dirName}")
            }
        }

        stage("Stashing JSON Patch files") {
            steps {
                echo "Stashing files within ${env.dirName}"
                // JHE: Mmmhh, are stashed files really kept for an eventuel next run: https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#stash-stash-some-files-to-be-used-later-in-the-build
                stash name: "${env.dirName}_stashed", includes: "${env.dirName}/*"
            }
        }

        stage("Getting Pkg projects from CVS") {
            steps {
                script {
                    def serviceNames = patchADFunctions.servicesInPatches(env.dirName)
                    patchADFunctions.coPackageProjects(serviceNames)
                }
            }
        }

        stage("Assembling projects") {
            steps {
                script {
                        patchADFunctions.assemble()
                    }
            }
        }

        stage("Deploying Artefacts") {
            steps {
                script {
                    patchADFunctions.deploy()
                }
            }
        }


    }
}