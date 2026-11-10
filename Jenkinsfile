pipeline {
    agent { label 'java' }

    tools {
        maven 'maven-default'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        skipDefaultCheckout(true)
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --format="%h %s"'
            }
        }

        stage('Verificar herramientas') {
            steps {
                sh '''
                    set -eu
                    java -version
                    mvn -version
                    git --version
                '''
            }
        }

        stage('Compilar JAR') {
            steps {
                sh 'mvn -B -ntp clean package -Dmaven.test.skip=true'
            }
        }

        stage('Verificar JAR') {
            steps {
                sh '''
                    test -s target/cia-api.jar
                    ls -lh target/cia-api.jar
                '''
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'target/cia-api.jar', fingerprint: true
            echo 'CIA API: JAR compilado y archivado. Las pruebas no se ejecutaron.'
        }
        failure {
            echo 'CIA API: falló la compilación o generación del JAR. Revisar Console Output.'
        }
    }
}
