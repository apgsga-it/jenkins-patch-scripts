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
            steps {
                echo "JSON Patch files will be taken from here"
                dirName = "20200624_12345"
                sh("mkdir ${20200624_12345}")
                sh("apsdbcli.sh -cpf Informatiktest,${20200624_12345}")
            }
        }
    }
}