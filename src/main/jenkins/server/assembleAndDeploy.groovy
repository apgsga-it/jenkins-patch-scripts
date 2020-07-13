library("patch-ad-functions-library")

pipeline {
    agent any
    environment {
        dirName = new Date().format("yyyyMMdd_HHmmssSSS")
        needAssemble = false
    }
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
                sh("mkdir ${env.dirName}")
                echo "Path of directory containing JSON Patch file for current pipeline execution: ${env.WORKSPACE}/${env.dirName}"
            }
        }

        stage("Getting JSON Patch files") {
            steps {
                // JHE: Seems that Jenkins declarative pipeline is using a non-shell script, meaning /etc/profile.d or .bashrc files are not getting interpreted
                // TODO JHE: 113 = Informatiktestlieferung Bearbeitung , will probably be retrieved from TargetSystemMapping. Or could also be a Pipeline Job parameter
                sh("/opt/apg-patch-cli/bin/apscli.sh -cpf 113,${env.dirName}")
                script {
                    if (new File(env.dirName).list().any()) {
                        echo "Patch to be assemble found, assembly will be done"
                        needAssemble = true
                    }
                }
            }
        }

        stage("Stashing JSON Patch files") {
            when {equals expected: true, actual: env.needAssemble} {
            steps {
                echo "TO BE REMOVED, env.needAssemble = ${env.needAssemble}"

                    script {
                        echo "Stashing files within ${env.dirName}"
                        // JHE: Mmmhh, are stashed files really kept for an eventuel next run: https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#stash-stash-some-files-to-be-used-later-in-the-build
                        stash name: "${env.dirName}_stashed", includes: "${env.dirName}/*"
                     }
                }
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

        stage("Assembling projects") {
            steps {
                script {
                        patchADFunctions.assemble(TARGET,"${env.WORKSPACE}/${env.dirName}")
                    }
            }
        }

        stage("Deploying Artefacts") {
            steps {
                script {
                    def serviceNames = patchADFunctions.servicesInPatches("${env.WORKSPACE}/${env.dirName}")
                    patchADFunctions.deploy(serviceNames)
                }
            }
        }


    }
}