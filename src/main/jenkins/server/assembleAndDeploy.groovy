pipeline {
    agent any
    environment {
        dirName = ""
    }
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
                script {
                    dirName = "20200624_12345"
                }
                echo "Name of directory containing JSON Patch file: ${env.dirName}"
            }
        }

        stage("Getting JSON Patch files") {
            steps {
                echo "JSON Patch files will be taken from here"
                sh("mkdir ${env.dirName}")
                sh("apsdbcli.sh -cpf Informatiktest,${env.dirName}")
            }
        }
    }
}