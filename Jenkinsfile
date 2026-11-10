pipeline {
    agent { label 'docker-build' }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        REGISTRY = 'registry:5000'
        IMAGE_NAME = 'cia-api'
        BUILDKIT_HOST = 'tcp://buildkitd:1234'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --format="%h %s"'
            }
        }

        stage('Verificar constructor') {
            steps {
                sh 'buildctl --addr "$BUILDKIT_HOST" debug workers'
            }
        }

        stage('Build y push al Registry interno') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'registry-ci-push',
                    usernameVariable: 'REG_USER',
                    passwordVariable: 'REG_PASS'
                )]) {
                    sh '''#!/bin/sh
set -eu
set +x
umask 077

export DOCKER_CONFIG
DOCKER_CONFIG="$(mktemp -d)"
trap 'rm -rf "$DOCKER_CONFIG"' EXIT

AUTH="$(printf '%s:%s' "$REG_USER" "$REG_PASS" | base64 | tr -d '\n')"
printf '{"auths":{"%s":{"auth":"%s"}}}\n' "$REGISTRY" "$AUTH" > "$DOCKER_CONFIG/config.json"

SHORT_SHA="$(git rev-parse --short=12 HEAD)"
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
        success {
            echo 'La imagen fue publicada en el Registry privado. No se desplegó la aplicación.'
        }
        failure {
            echo 'Falló la construcción o publicación. Revisar Console Output.'
        }
    }
}
