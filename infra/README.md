# CIA API — compilación de imágenes y Registry del VPS

Este directorio aporta los Compose para implementar desde **Hostinger Docker Manager**.
Se usa el Caddy existente; no publicar puertos 5000 o 1234 directamente en el VPS.

## Topología

GitHub push -> Caddy /github-webhook/ -> Jenkins job `cia-api-image` ->
Agente `docker-build-01` con cliente `buildctl` ->
BuildKit rootless (red Docker privada) -> Registry v3 via HTTPS de Caddy.

## Preparación

1. Crear registro DNS A `registry.devops.leenustechs.com.mx` -> IP del VPS.
2. Crear volumen `platform_registry_auth` y generar `/auth/htpasswd` bcrypt sin publicar la contraseña:
   ```bash
   docker volume create platform_registry_auth
   docker run --rm -it -v platform_registry_auth:/auth --entrypoint htpasswd httpd:2.4-alpine -B -c /auth/htpasswd ci-publisher
   ```
   Atención: `-c` reemplaza el archivo si se ejecuta de nuevo.
3. Desplegar `infra/registry.compose.yml` como `platform-registry`.
4. Agregar a Caddy, en su Caddyfile existente:
   ```caddyfile
   registry.devops.leenustechs.com.mx {
       encode zstd gzip
       reverse_proxy registry:5000
   }
   ```
   NO usar `basic_auth` de Caddy en ese dominio: la autenticación bcrypt corresponde al Registry.
   Los dominios previos de Caddy se conservan.
5. Verificar `curl -I https://registry.devops.leenustechs.com.mx/v2/` (401 esperado sin credenciales).
6. Construir en el VPS la imagen del agente (CLI, sin cambiar AlmaLinux):
   ```bash
   curl -fsSL https://raw.githubusercontent.com/renelee121/leenustechs-cia-api/ci/docker-registry/infra/Dockerfile.jenkins-image-agent | docker build -t leenustechs/jenkins-image-agent:buildctl-1 -f - .
   ```
7. Crear nodo Jenkins permanente `docker-build-01`: 1 ejecutor, root `/home/jenkins/agent`, etiqueta `docker-build`, conectar al controlador, uso solo con etiqueta.
8. Sustituir `REEMPLAZAR_SECRET_NODO_JENKINS` en `image-builder.compose.yml` por secreto del nodo y desplegar como proyecto separado `platform-jenkins-image-builder`.
9. Crear credenciales de Jenkins de tipo Username with password, ID `registry-ci-push`, usuario `ci-publisher`, contraseña del Registry.
10. Crear job Pipeline `cia-api-image`: `Pipeline script from SCM`, Git URL `https://github.com/renelee121/leenustechs-cia-api.git`, rama `*/ci/docker-registry`, script `Jenkinsfile`, trigger GitHub hook for GITScm polling.
11. Ejecutar una compilación manual inicial; luego probar con commit + push en la rama.

El `Dockerfile` compila con JDK 21 (exigencia del pom.xml) y ejecuta sobre Temurin 25 JRE. **Se omiten tests** deliberadamente. Solo se publica una imagen con tag SHA abreviado, sin ejecutar servicios de la aplicación.

**Seguridad**: BuildKit escucha TCP 1234 sin TLS, restringido exclusivamente a `buildnet`; NO publicar ese puerto. Se ejecuta rootless, con seccomp/AppArmor más permisivos para permitir build dentro de Docker. Evaluar este compromiso antes de ejecutar código no confiable. No montar el socket /var/run/docker.sock del VPS.
