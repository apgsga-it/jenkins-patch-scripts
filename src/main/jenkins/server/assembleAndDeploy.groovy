library("patch-functions-library")

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

        stage("Getting Pkg projects from CVS") {
            steps {

                echo "Trying to call a function from a library ..."
                patchfunctions.testjhefunc()

                /*
                def cvsBranch = patchConfig.microServiceBranch
                if(type.equals("db")) {
                    cvsBranch = patchConfig.dbPatchBranch
                }
                def callBack = benchmark()
                def duration = callBack {
                    checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                            [compressionLevel: -1, cvsRoot: patchConfig.cvsroot, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
                                    [location: [$class: 'BranchRepositoryLocation', branchName: cvsBranch, useHeadIfNotFound: false],  modules: [
                                            [localName: moduleName, remoteName: moduleName]
                                    ]]
                            ]]
                    ], skipChangeLog: false])
                }

                 */
            }
        }


    }
}