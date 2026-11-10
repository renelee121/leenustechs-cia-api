# CIA API: Registry Docker 100% interno al VPS

Los cambios viven en la rama **CIDC/DEV**. El Registry no tiene DNS público, Caddy ni puertos publicados.
Se despliega mediante dos proyectos en Hostinger Docker Manager:

- `platform-registry`: registry:3 con almacenamiento persistente y autenticación htpasswd.
- `platform-jenkins-image-builder`: agente Jenkins buildctl + BuildKit rootless, sin socket Docker del host.

El Dockerfile de la raíz ya compila con JDK 21 y ejecuta con Eclipse Temurin 25. Las pruebas se omiten.

## 1. Red y credencial (una vez, en consola del VPS)
```bash
docker network create platform_ci_registry
docker volume create platform_registry_auth
docker run --rm -it \
  -v platform_registry_auth:/auth \
  --entrypoint htpasswd httpd:2.4-alpine \
  -B -c /auth/htpasswd ci-publisher
```
`-c` crea/sobrescribe el fichero; no volver a usar `-c` para añadir usuarios.
No compartir contraseñas. El Registry usa HTTP dentro de una red dedicada y requiere autenticación.

## 2. Registry desde Hostinger
Crear proyecto `platform-registry` pegando `infra/registry.compose.yml`.
No agregar `ports:`. No conectarlo a `platform_edge`. No crear DNS ni modificar Caddy.

Comprobar desde un contenedor temporal en la red:
```bash
docker run --rm --network platform_ci_registry \
  curlimages/curl:8.11.1 -sS -o /dev/null \
  -w 'HTTP %{http_code}\n' http://registry:5000/v2/
```
Debe devolver `401` si está funcionando y solicita credenciales.

## 3. Construir agente Docker (sin instalar nada en AlmaLinux)
```bash
curl -fsSL \
  https://raw.githubusercontent.com/renelee121/leenustechs-cia-api/CIDC/DEV/infra/Dockerfile.jenkins-build-agent \
  | docker build -t leenustechs/jenkins-build-agent:buildctl-1 -f - .
```
La imagen utiliza Jenkins inbound-agent e incorpora `buildctl`.

## 4. Configurar nodo en Jenkins
Crear nodo permanente `docker-build-01` con:
- 1 ejecutor
- `/home/jenkins/agent` como raíz
- etiqueta `docker-build`
- uso: solo jobs que coincidan con la etiqueta
- lanzamiento: agente conecta al controlador

El secret del nodo se pega en el Compose de agente en Hostinger (no en GitHub).

## 5. Desplegar builder desde Hostinger
Crear proyecto `platform-jenkins-image-builder` con `infra/image-builder.compose.yml`.
Sustituir `REEMPLAZAR_SECRET_NODO_JENKINS` por el secreto Jenkins **solo en el Compose de Hostinger**.
El BuildKit rootless recibe un TOML incrustado por Compose con `[registry."registry:5000"] http = true`.
El puerto TCP 1234 de BuildKit es interno, sin TLS; limitar estrictamente qué contenedores se unen a `platform_ci_registry`.
La compatibilidad de rootless con Docker/Kernel debe validarse: requiere seccomp/AppArmor menos restrictivos.

## 6. Credencial del Registry en Jenkins
Administrar Jenkins → Credentials → Username with password
- ID: `registry-ci-push`
- Usuario: `ci-publisher`
- Contraseña: la de `htpasswd`

## 7. Crear tarea CI automática
Nueva tarea `cia-api-image` → Pipeline → Pipeline script from SCM:
- Git URL: `https://github.com/renelee121/leenustechs-cia-api.git`
- Branch Specifier: `*/CIDC/DEV` (respetar mayúsculas)
- Script Path: `Jenkinsfile`
- Build Triggers: **GitHub hook trigger for GITScm polling**

El webhook existente de GitHub hacia Jenkins se reutiliza.
Primero ejecutar una vez manualmente, luego validar el siguiente push a CIDC/DEV.

## Resultado
El pipeline construye con BuildKit y hace push de
`registry:5000/cia-api:<SHA-12>`.
No hay despliegue de la aplicación.

**Límite importante:** el nombre DNS `registry` solo se resuelve dentro de la red Docker.
El Docker Engine del host NO puede hacer `docker pull registry:5000/...` directamente sin configurar un endpoint accesible desde el host y confianza en HTTP. Si algún día se requiere descarga desde fuera de esa red, se diseñará acceso separado y autenticado.
