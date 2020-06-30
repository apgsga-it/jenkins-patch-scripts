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
            }
        }

        stage("Getting JSON Patch files") {
            steps {
                echo "JSON Patch files will be taken from here"
                sh("mkdir ${env.dirName}")
                // JHE: Seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf Informatiktest,${env.dirName}")
            }
        }

        stage("Stashing JSON Patch files") {
            steps {
                echo "Stashing files within ${env.dirName}"
                // JHE: Mmmhh, are stashed files really kept for an eventuel next run: https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#stash-stash-some-files-to-be-used-later-in-the-build
                stash name: "${env.dirName}_stashed", includes: "${env.dirName}/*"
            }
        }

        stage("test input") {
            steps {
                input id: "test", message: "Here you should restart jenkins and see what happens with stashed files"
            }
        }

        stage("TEST STAGE TO BE DELETED") {
            steps {
                echo "Unstashing files within ${env.dirName}"
                sh("mkdir ${env.dirName}_testUnstashed")
                dir("${env.dirName}_testUnstashed") {
                    unstash name: "${env.dirName}_stashed"
                }
            }
        }
    }
}