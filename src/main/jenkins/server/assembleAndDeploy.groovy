pipeline {
    agent any
    environment {
        dirName = ""
    }
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
            }
        }
        stage("Getting JSON Patch files") {

            script {
                dirName = "20200624_12345"
            }

            steps {
                echo "JSON Patch files will be taken from here"
                sh("mkdir ${env.dirName}")
                sh("apsdbcli.sh -cpf Informatiktest,${env.dirName}")
            }
        }
    }
}