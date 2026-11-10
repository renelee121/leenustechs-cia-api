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

                    if ! command -v redis-server >/dev/null 2>&1; then
                      echo "ERROR: Las pruebas de CIA API requieren redis-server en el agente Jenkins."
                      echo "Instala Redis dentro de la imagen del agente Docker; no en AlmaLinux."
                      exit 2
                    fi

                    redis-server --version
                '''
            }
        }

        stage('Compilar y probar') {
            steps {
                sh 'mvn -B -ntp clean verify'
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true, fingerprint: true
        }
        success {
            echo 'CIA API: compilación y pruebas correctas. Artefacto JAR archivado.'
        }
        failure {
            echo 'CIA API: revisar los logs y los reportes de pruebas.'
        }
    }
}
