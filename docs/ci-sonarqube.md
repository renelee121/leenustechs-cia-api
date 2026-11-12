# SonarQube estricto - implementación pendiente de activación en Jenkins

Este cambio está en una rama feature; CIDC/DEV y Jenkins CI/CD existentes permanecen sin cambios.

## Configuración requerida

1. SonarQube: usar **Standard Experience** (los indicadores bugs y code_smells no existen igual en MQR). Crear proyecto con key `mx.com.leenustechs:cia-api` y asignar un Quality Gate personalizado con tres condiciones sobre **Overall Code**: Bugs > 0, Code Smells > 0, Coverage < 80%.
2. Jenkins: instalar/verificar plugin **SonarQube Scanner for Jenkins** y registrar una instalación llamada exactamente **SonarQube DEV**; token de análisis en Jenkins Credentials (Secret text). URL recomendada: `http://platform-sonarqube-sonarqube-1:9000` sobre la red Docker privada `platform_sonar_proxy`; evitar el acceso público protegido por Basic Auth de Caddy.
3. En el Compose del agente Jenkins con label `java`, añadir persistentemente la red externa `platform_sonar_proxy`. Verificar que el agente disponga de JDK 21, Maven >= 3.9.16 y <4, `Mockito (sin servidor Redis)` y `curl` en PATH. Las pruebas utilizan mocks con Mockito; no se inicia Redis ni se conecta a Valkey DEV.
4. Jenkins > Manage Jenkins > System > Global properties: definir `REGISTRY=registry:5000` y `BUILDKIT_HOST=tcp://buildkitd:1234`. Mantener la credencial de Registry `registry-ci-push` sin secretos en repositorio.
5. Job **leenustechs-cia-api-ci**: emplear **Pipeline script from SCM** con Script Path `Jenkinsfile`. Probar primero la rama `feature/sonarqube-strict-ci`; no dejar un script antiguo embebido en la interfaz.
6. Antes de probar una rama feature, impedir que el job **leenustechs-cia-api-cd** despliegue por un SUCCESS de ensayo; reactivar su disparador solamente al adoptar los cambios en CIDC/DEV.

## Flujo garantizado por Jenkinsfile

Checkout → Maven `clean verify` y JaCoCo XML → SonarScanner esperando Quality Gate → comprobación adicional fail-closed con métricas generales `bugs=0`, `code_smells=0`, `coverage>=80` → stash de JAR aprobado → BuildKit + push por SHA.

Si falla cualquier fase anterior, **BuildKit no comienza y no se publica una imagen nueva**. El Dockerfile es runtime-only y no ejecuta Maven ni omite tests.

Nota: no se ha ejecutado CI real. Las métricas podrían fallar inicialmente; deberán corregirse código y pruebas antes de aceptar la rama.
