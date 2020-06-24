pipeline {

    stages {
        stage("Initializing") {
            echo "Pipeline is running for target ${TARGET}"
        }
        stage("Getting JSON Patch files") {
            echo "JSON Patch files will be taken from here"
            sh("ls -la")
        }
    }

}