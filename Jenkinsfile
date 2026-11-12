pipeline {
    agent none
    options {
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    environment {
        IMAGE_NAME = 'cia-api'
    }
    stages {
        stage('Tests y calidad') {
            agent { label 'java' }

            tools {
                maven 'Maven-3.10.0'
            }
            
            stages {
                stage('Checkout') {
                    steps {
                        checkout scm
                        sh 'git log -1 --format="%h %s"'
                    }
                }
                stage('Maven verify y JaCoCo') {
                    steps {
                        sh '''#!/bin/sh
set -eu
command -v mvn >/dev/null || { echo "Falta Maven"; exit 1; }
mvn -B -ntp clean verify
test -s target/cia-api.jar
test -s target/site/jacoco/jacoco.xml
'''
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('SonarQube Quality Gate') {
                    steps {
                        withSonarQubeEnv('SonarQube DEV') {
                            sh '''#!/bin/sh
set -eu
set +x
test -n "$SONAR_AUTH_TOKEN" && test -n "$SONAR_HOST_URL"
export SONAR_TOKEN="$SONAR_AUTH_TOKEN"
mvn -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar \
  -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=600
'''
                        }
                    }
                }
                stage('Metricas estrictas de todo el codigo') {
                    steps {
                        withSonarQubeEnv('SonarQube DEV') {
                            script {
                                def raw = sh(
                                  script: '''#!/bin/sh
set -eu
set +x
command -v curl >/dev/null
test -n "$SONAR_AUTH_TOKEN" && test -n "$SONAR_HOST_URL"
umask 077
cfg="$(mktemp)"
trap 'rm -f "$cfg"' EXIT
printf 'header = "Authorization: Bearer %s"\\n' "$SONAR_AUTH_TOKEN" > "$cfg"
curl -fsS --max-time 30 --config "$cfg" \
  "$SONAR_HOST_URL/api/measures/component?component=mx.com.leenustechs:cia-api&metricKeys=bugs,code_smells,coverage"
''',
                                  returnStdout: true
                                ).trim()
                                def parsed = new groovy.json.JsonSlurperClassic().parseText(raw)
                                def metrics = [:]
                                (parsed.component?.measures ?: []).each { item ->
                                    metrics[item.metric] = item.value
                                }
                                ['bugs', 'code_smells', 'coverage'].each { key ->
                                    if (metrics[key] == null || !metrics[key].toString().matches('[0-9]+(\\.[0-9]+)?')) {
                                        error('Metrica SonarQube invalida: ' + key + '. Revisar Standard Experience.')
                                    }
                                }
                                def bugs = new BigDecimal(metrics.bugs.toString())
                                def smells = new BigDecimal(metrics.code_smells.toString())
                                def coverage = new BigDecimal(metrics.coverage.toString())
                                echo 'Calidad general: bugs=' + bugs + ', smells=' + smells + ', coverage=' + coverage + '%'
                                if (bugs != 0 || smells != 0 || coverage < 80) {
                                    error('No se construye imagen: requiere 0 bugs, 0 code smells, coverage >=80%')
                                }
                            }
                        }
                    }
                }
                stage('Aprobar artefacto') {
                    steps {
                        sh '''#!/bin/sh
set -eu
test -s target/cia-api.jar
git rev-parse --short=12 HEAD > .ci-source-sha
'''
                        stash name: 'approved-image',
                            includes: 'Dockerfile,.dockerignore,target/cia-api.jar,.ci-source-sha'
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/site/jacoco/**,target/surefire-reports/*.xml',
                        allowEmptyArchive: true
                }
            }
        }
        stage('Build y push al Registry interno') {
            agent { label 'docker-build' }
            steps {
                deleteDir()
                unstash 'approved-image'
                sh '''#!/bin/sh
set -eu
test -n "$REGISTRY" && test -n "$BUILDKIT_HOST"
test -s target/cia-api.jar
buildctl --addr "$BUILDKIT_HOST" debug workers
'''
                withCredentials([usernamePassword(
                    credentialsId: 'registry-ci-push',
                    usernameVariable: 'REG_USER',
                    passwordVariable: 'REG_PASS'
                )]) {
                    sh '''#!/bin/sh
set -eu
set +x
umask 077
export DOCKER_CONFIG="$(mktemp -d)"
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
AUTH="$(printf '%s:%s' "$REG_USER" "$REG_PASS" | base64 | tr -d '\\n')"
printf '{"auths":{"%s":{"auth":"%s"}}}\\n' "$REGISTRY" "$AUTH" > "$DOCKER_CONFIG/config.json"
SHORT_SHA="$(cat .ci-source-sha)"
IMAGE_REF="$REGISTRY/$IMAGE_NAME:$SHORT_SHA"
buildctl --addr "$BUILDKIT_HOST" build \
  --progress=plain \
  --frontend=dockerfile.v0 \
  --local context=. \
  --local dockerfile=. \
  --output "type=image,name=$IMAGE_REF,push=true,registry.insecure=true"
echo "IMAGEN PUBLICADA: $IMAGE_REF"
'''
                }
            }
        }
    }
    post {
        success { echo 'Tests y SonarQube aprobados; imagen publicada.' }
        failure { echo 'CI bloqueada; ninguna imagen nueva se genera antes de aprobar todas las fases.' }
    }
}
