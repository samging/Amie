package org.example.amiepackagerepository.shared.integration.github.service

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriveServiceTest {
    @Test
    fun `downloadFile - happy path - downloads first matching file successfully`() {
        // Scenario: Files are found on Google Drive; executes download to target file
        val fileName = "target_document.pdf"
        val savePath = File.createTempFile("download_test_", ".pdf").apply { deleteOnExit() }
        val matchedFileId = "drive-file-id-123"

        val mockDriveFile = DriveFile().apply {
            id = matchedFileId
            name = fileName
        }
        val fileList = FileList().setFiles(listOf(mockDriveFile))

        // Stub Drive files().list() chain
        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setQ("name = '$fileName' and trashed = false")).thenReturn(listMock)
        whenever(listMock.setSpaces("drive")).thenReturn(listMock)
        whenever(listMock.setFields("files(id, name)")).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(true)).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(true)).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(fileList)

        // Stub Drive files().get(fileId) chain
        whenever(filesMock.get(matchedFileId)).thenReturn(getMock)

        // Act
        driveService.downloadFile(driveClient, fileName, savePath)

        // Assert
        verify(getMock).executeAndDownloadTo(any())
        verify(logger).info("File '{}' downloaded successfully.", fileName)
    }

    @Test
    fun `downloadFile - multiple files match - defaults to downloading the first match`() {
        // Scenario: Multiple files match the search criteria; verify only the first file ID is downloaded
        val fileName = "duplicate_name.csv"
        val savePath = File.createTempFile("download_multi_test_", ".csv").apply { deleteOnExit() }
        val firstFileId = "first-file-id-001"
        val secondFileId = "second-file-id-002"

        val file1 = DriveFile().apply { id = firstFileId; name = fileName }
        val file2 = DriveFile().apply { id = secondFileId; name = fileName }
        val fileList = FileList().setFiles(listOf(file1, file2))

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setQ(any())).thenReturn(listMock)
        whenever(listMock.setSpaces(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(fileList)

        whenever(filesMock.get(firstFileId)).thenReturn(getMock)

        // Act
        driveService.downloadFile(driveClient, fileName, savePath)

        // Assert
        verify(filesMock).get(firstFileId)
        verify(filesMock, never()).get(secondFileId)
        verify(getMock).executeAndDownloadTo(any())
    }

    @Test
    fun `downloadFile - file not found - throws IOException when file list is empty or null`() {
        // Scenario: Drive API returns zero matching files; throws IOException
        val fileName = "non_existent.txt"
        val savePath = File.createTempFile("download_empty_test_", ".txt").apply { deleteOnExit() }
        val emptyFileList = FileList().setFiles(emptyList())

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setQ(any())).thenReturn(listMock)
        whenever(listMock.setSpaces(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(emptyFileList)

        // Act & Assert
        val exception = assertThrows<IOException> {
            driveService.downloadFile(driveClient, fileName, savePath)
        }

        assertEquals("File not found on Google Drive: $fileName", exception.message)
        verify(logger).error("File '{}' not found on Google Drive.", fileName)
        verify(filesMock, never()).get(any())
    }

    @Test
    fun `downloadFile - drive list api throws - propagates exception when search query fails`() {
        // Scenario: Drive API fails during search listing (e.g., Network Error or Invalid Credentials)
        val fileName = "error_file.txt"
        val savePath = File.createTempFile("download_api_err_", ".txt").apply { deleteOnExit() }
        val apiErrorMessage = "403 Forbidden: Insufficient Permissions"

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setQ(any())).thenReturn(listMock)
        whenever(listMock.setSpaces(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenThrow(IOException(apiErrorMessage))

        // Act & Assert
        val exception = assertThrows<IOException> {
            driveService.downloadFile(driveClient, fileName, savePath)
        }

        assertEquals(apiErrorMessage, exception.message)
        verify(filesMock, never()).get(any())
    }

    @Test
    fun `downloadFile - drive download api throws - propagates exception when executeAndDownloadTo fails`() {
        // Scenario: File is found, but streaming from Drive fails mid-transfer
        val fileName = "corrupt_stream.zip"
        val savePath = File.createTempFile("download_stream_err_", ".zip").apply { deleteOnExit() }
        val fileId = "valid-file-id-999"
        val downloadErrorMessage = "Connection reset by peer"

        val file1 = DriveFile().apply { id = fileId; name = fileName }
        val fileList = FileList().setFiles(listOf(file1))

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setQ(any())).thenReturn(listMock)
        whenever(listMock.setSpaces(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(fileList)

        whenever(filesMock.get(fileId)).thenReturn(getMock)
        whenever(getMock.executeAndDownloadTo(any())).thenThrow(IOException(downloadErrorMessage))

        // Act & Assert
        val exception = assertThrows<IOException> {
            driveService.downloadFile(driveClient, fileName, savePath)
        }

        assertEquals(downloadErrorMessage, exception.message)
    }

    @Test
    fun `listFiles - happy path - returns formatted list of file names when files exist`() {
        // Arrange: Drive returns a list with multiple files
        val file1 = DriveFile().apply { id = "id-1"; name = "document.pdf" }
        val file2 = DriveFile().apply { id = "id-2"; name = "spreadsheet.xlsx" }
        val fileList = FileList().setFiles(listOf(file1, file2))

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setPageSize(10)).thenReturn(listMock)
        whenever(listMock.setFields("nextPageToken, files(id, name)")).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(true)).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(true)).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(fileList)

        // Act
        val result = driveService.listFiles(driveClient)

        // Assert
        val expectedOutput = "Available Files: \ndocument.pdf\nspreadsheet.xlsx\n"
        assertEquals(expectedOutput, result)

        // Verify request parameters and logging
        verify(listMock).setPageSize(10)
        verify(listMock).setFields("nextPageToken, files(id, name)")
        verify(listMock).setSupportsAllDrives(true)
        verify(listMock).setIncludeItemsFromAllDrives(true)
        verify(logger).info("Found {} files in Google Drive", 2)
    }

    @Test
    fun `listFiles - empty list - returns 'Is null or empty' when file list is empty`() {
        // Arrange: Drive returns an empty file list
        val emptyFileList = FileList().setFiles(emptyList())

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setPageSize(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(emptyFileList)

        // Act
        val result = driveService.listFiles(driveClient)

        // Assert
        assertEquals("Is null or empty.", result)
        verify(logger).info("No Google Drive files found.")
    }

    @Test
    fun `listFiles - null files property - returns 'Is null or empty' when files field is null`() {
        // Arrange: Drive response object has null files property
        val nullFilesResult = FileList().setFiles(null)

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setPageSize(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenReturn(nullFilesResult)

        // Act
        val result = driveService.listFiles(driveClient)

        // Assert
        assertEquals("Is null or empty.", result)
        verify(logger).info("No Google Drive files found.")
    }

    @Test
    fun `listFiles - drive api exception - propagates exception when execute fails`() {
        // Arrange: Drive API throws an IOException during listing
        val apiErrorMessage = "401 Unauthorized"

        whenever(driveClient.files()).thenReturn(filesMock)
        whenever(filesMock.list()).thenReturn(listMock)
        whenever(listMock.setPageSize(any())).thenReturn(listMock)
        whenever(listMock.setFields(any())).thenReturn(listMock)
        whenever(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        whenever(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        whenever(listMock.execute()).thenThrow(IOException(apiErrorMessage))

        // Act & Assert
        val exception = assertThrows<IOException> {
            driveService.listFiles(driveClient)
        }

        assertEquals(apiErrorMessage, exception.message)
    }
    
}