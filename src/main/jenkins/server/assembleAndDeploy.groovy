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
                sh("apsdbcli.sh -cpf Informatiktest,${env.dirName}")
            }
        }
    }
}