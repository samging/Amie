package org.example.amiepackagerepository.shared.integration.gdrive.configuration

import com.google.api.services.drive.Drive
import org.example.amiepackagerepository.shared.transactionalMiddleware.integration.gdrive.configuration.GoogleDriveClientConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.test.util.ReflectionTestUtils
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleDriveClientConfigTest {
    private lateinit var config: GoogleDriveClientConfig

    @BeforeEach
    fun setUp() {
        config = GoogleDriveClientConfig()
    }

    @Test
    fun `connectToDisk - missing file - throws IllegalStateException when file does not exist`() {
        val nonExistentPath = "/path/to/non/existent/file.json"
        ReflectionTestUtils.setField(config, "packageLock", nonExistentPath)

        val exception = assertThrows<IllegalStateException> {
            config.connectToDisk()
        }

        assertTrue(exception.message!!.contains("Credentials file not found at $nonExistentPath"))
    }

    @Test
    fun `connectToDisk - wrong key type - throws helpful IllegalStateException when OAuth client file passed`(
        @TempDir tempDir: File
    ) {
        val oauthJsonFile = File(tempDir, "oauth-client.json").apply {
            writeText("""{"installed": {"client_id": "123", "type": "installed"}}""")
        }
        ReflectionTestUtils.setField(config, "packageLock", oauthJsonFile.absolutePath)

        val exception = assertThrows<IllegalStateException> {
            config.connectToDisk()
        }

        assertTrue(exception.message!!.contains("is an OAuth Client ID file, but this service currently expects a Service Account key"))
    }

    @Test
    fun `connectToDisk - valid service account key - initializes Drive bean successfully`(@TempDir tempDir: File) {
        val serviceAccountJson = File(tempDir, "service-account.json").apply {
            //[NOTE to Reader] it's not the real private key, check .env files
            writeText(
                """
                {
                  "type": "service_account",
                  "project_id": "test-project",
                  "private_key_id": "key-id",
                  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFMMSCBKgwggSkAgEAAoIBAQCCoI\n-----END PRIVATE KEY-----\n",
                  "client_email": "test@test-project.iam.gserviceaccount.com",
                  "client_id": "123456789",
                  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """.trimIndent()
            )
        }
        ReflectionTestUtils.setField(config, "packageLock", serviceAccountJson.absolutePath)
        val driveBean: Drive = config.connectToDisk()

        assertNotNull(driveBean)
        assertEquals("AmiePackageRepository", driveBean.applicationName)
    }
}