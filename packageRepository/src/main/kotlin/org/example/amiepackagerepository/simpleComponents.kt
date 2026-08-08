package org.example.amiepackagerepository

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.io.InputStream
import java.util.Collections

/**
 * Spring configuration class to initialize and manage the Google Drive API client.
 */
@Configuration
class ConnectToGoogleDrive {

	/**
	 * Creates and configures the [Drive] client bean.
	 * Supports both Service Account and User Account (OAuth2) credentials.
	 */
	@Bean
	fun connectToDisk(): Drive {

		val configPath = System.getenv("b")
			?: "/Users/samuel/Downloads/amieServicePackages.json" // Primary fallbackuy7hj6y6t5grtgr5t5grt5

		val gFile = java.io.File(configPath)
		if (!gFile.exists()) {
			throw IllegalStateException("Credentials file not found at $configPath. Please check AMIE_GDISK_OA env var.")
		}

		val credentials = try {
			GoogleCredentials.fromStream(gFile.inputStream())
				.createScoped(Collections.singleton(DriveScopes.DRIVE))
		} catch (e: Exception) {
			if (e.message?.contains("type") == true) {
				throw IllegalStateException(
					"The file at $configPath is an OAuth Client ID file, but this service currently expects a Service Account key. " +
					"Please point AMIE_GDISK_OA to '/Users/samuel/Downloads/amieServicePackages.json' instead.", e
				)
			}
			throw e
		}

		return Drive.Builder(
			GoogleNetHttpTransport.newTrustedTransport(),
			GsonFactory.getDefaultInstance(),
			HttpCredentialsAdapter(credentials)
		)
			.setApplicationName("AmiePackageRepository")
			.build()
	}
}
