pipeline {
    agent { label 'docker-build' }

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        skipDefaultCheckout(true)
    }

    environment {
        REGISTRY_HOST = 'registry.devops.leenustechs.com.mx'
        IMAGE_REPOSITORY = 'cia-api'
        BUILDKIT_HOST = 'tcp://buildkitd:1234'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --format="%h %s"'
            }
        }

        stage('Verificar BuildKit') {
            steps {
                sh 'buildctl --addr "$BUILDKIT_HOST" debug workers'
            }
        }

        stage('Construir y publicar imagen') {
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

DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
trap 'rm -rf "$DOCKER_CONFIG"' EXIT

AUTH="$(printf '%s:%s' "$REG_USER" "$REG_PASS" | base64 | tr -d '\n')"
printf '{"auths":{"%s":{"auth":"%s"}}}\n' \
    "$REGISTRY_HOST" "$AUTH" > "$DOCKER_CONFIG/config.json"

IMAGE_TAG="$(git rev-parse --short=12 HEAD)"
IMAGE_REF="$REGISTRY_HOST/$IMAGE_REPOSITORY:$IMAGE_TAG"

buildctl --addr "$BUILDKIT_HOST" build \
    --progress=plain \
    --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=. \
    --output "type=image,name=$IMAGE_REF,push=true"

echo "Imagen publicada: $IMAGE_REF"
'''
                }
            }
        }
    }

    post {
        success {
            echo 'Imagen construida y enviada al Registry; sin despliegue a producción.'
        }
        failure {
            echo 'Fallo en la construcción o el push de imagen. Revisar Console Output.'
        }
    }
}
