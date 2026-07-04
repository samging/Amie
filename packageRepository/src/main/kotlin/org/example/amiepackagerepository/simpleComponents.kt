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
 *
 * This class reads the service account credentials from the classpath resources
 * and exposes a thread-safe [Drive] bean for dependency injection.
 *
 * @author /sam
 */
@Configuration
class ConnectToGoogleDrive {

	companion object {
		private const val APPLICATION_NAME = "Spring Boot API for Google Drive"
	}

	/**
	 * Creates and configures the [Drive] client bean using OAuth2 Service Account credentials.
	 *
	 * The method expects a valid credentials JSON file named `amieServicePackages.json`
	 * to be present in the root of the resources directory.
	 *
	 * @return An authorized [Drive] client instance with full drive scopes.
	 * @throws IOException If the credentials file cannot be found or read.
	 */
	@Bean
	fun connectToDisk(): Drive {
		val gStream: InputStream = ConnectToGoogleDrive::class.java.getResourceAsStream("/amieServicePackages.json")
			?: throw IOException("Resource not found: credentials.json")

		val credentials = GoogleCredentials.fromStream(gStream)
			.createScoped(Collections.singleton(DriveScopes.DRIVE))

		return Drive.Builder(
			GoogleNetHttpTransport.newTrustedTransport(),
			GsonFactory.getDefaultInstance(),
			HttpCredentialsAdapter(credentials)
		)
			.setApplicationName(APPLICATION_NAME)
			.build()
	}
}