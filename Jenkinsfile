pipeline {
  agent any
  stages {
    stage('build') {
      steps {
        sh 'mvn clean compile package install assembly:assembly'
      }
    }
  }
}