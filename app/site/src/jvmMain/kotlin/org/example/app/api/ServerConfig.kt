package org.example.app.api

/** Where this site's server reaches the other Amie services. */
object ServerConfig {
    private fun env(name: String) = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    /** The Spring Boot packageRepository (`AMIE_BACKEND_URL`, default `http://localhost:8080`). */
    val backendUrl: String get() = (env("AMIE_BACKEND_URL") ?: "http://localhost:8080").trimEnd('/')

    /** The awsS3 service that lists and serves jars (`AMIE_S3_SERVICE_URL`, default `http://localhost:8088`). */
    val s3ServiceUrl: String get() = (env("AMIE_S3_SERVICE_URL") ?: "http://localhost:8088").trimEnd('/')

    fun backend(path: String) = "$backendUrl/${path.trimStart('/')}"
    fun s3Service(path: String) = "$s3ServiceUrl/${path.trimStart('/')}"
}
