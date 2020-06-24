pipeline {
    agent any
    stages {
        stage("Initializing") {
            steps {
                echo "Pipeline is running for target ${TARGET}"
            }
        }
        stage("Getting JSON Patch files") {
            steps {
                echo "JSON Patch files will be taken from here"
                sh("ls -la")
            }
        }
    }

}