package org.example.amiepackagerepository.shared.integration.github.service

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import com.google.common.base.CharMatcher.any
import okhttp3.internal.concurrent.TaskRunner.Companion.logger
import org.example.amiepackagerepository.shared.transactionalMiddleware.integration.gdrive.DriveService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.slf4j.Logger


class DriveServiceTest {
    @Mock
    private lateinit var driveClient: Drive

    @Mock
    private lateinit var listMock: Drive.Files.List
    @Mock
    private lateinit var filesMock: Drive.Files

    @Mock
    private lateinit var getMock: Drive.Files.Get

    @Mock
    private lateinit var driveService: DriveService

    @Test
    fun `downloadFile - happy path - downloads first matching file successfully`() {
        val fileName = "target_document.pdf"
        val savePath = File.createTempFile("download_test_", ".pdf").apply { deleteOnExit() }
        val matchedFileId = "drive-file-id-123"

        val mockDriveFile = DriveFile().apply {
            id = matchedFileId
            name = fileName
        }
        val fileList = FileList().setFiles(listOf(mockDriveFile))

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setQ("name = '$fileName' and trashed = false")).thenReturn(listMock)
        `when`(listMock.setSpaces("drive")).thenReturn(listMock)
        `when`(listMock.setFields("files(id, name)")).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(true)).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(true)).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(fileList)

        `when`(filesMock.get(matchedFileId)).thenReturn(getMock)

        driveService.downloadFile(driveClient, fileName, savePath)

        verify(getMock).executeAndDownloadTo(any())
        verify(logger).info("File '{}' downloaded successfully.", fileName)
    }

    @Test
    fun `downloadFile - multiple files match - defaults to downloading the first match`() {
        val fileName = "duplicate_name.csv"
        val savePath = File.createTempFile("download_multi_test_", ".csv").apply { deleteOnExit() }
        val firstFileId = "first-file-id-001"
        val secondFileId = "second-file-id-002"

        val file1 = DriveFile().apply { id = firstFileId; name = fileName }
        val file2 = DriveFile().apply { id = secondFileId; name = fileName }
        val fileList = FileList().setFiles(listOf(file1, file2))

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setQ(any())).thenReturn(listMock)
        `when`(listMock.setSpaces(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(fileList)

        `when`(filesMock.get(firstFileId)).thenReturn(getMock)

        driveService.downloadFile(driveClient, fileName, savePath)

        verify(filesMock).get(firstFileId)
        verify(filesMock, never()).get(secondFileId)
        verify(getMock).executeAndDownloadTo(any())
    }

    @Test
    fun `downloadFile - file not found - throws IOException when file list is empty or null`() {
        val fileName = "non_existent.txt"
        val savePath = File.createTempFile("download_empty_test_", ".txt").apply { deleteOnExit() }
        val emptyFileList = FileList().setFiles(emptyList())

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setQ(any())).thenReturn(listMock)
        `when`(listMock.setSpaces(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(emptyFileList)

        val exception = assertThrows<IOException> {
            driveService.downloadFile(driveClient, fileName, savePath)
        }

        assertEquals("File not found on Google Drive: $fileName", exception.message)
        verify(logger).error("File '{}' not found on Google Drive.", fileName)
        verify(filesMock, never()).get(any())
    }

    @Test
    fun `downloadFile - drive list api throws - propagates exception when search query fails`() {
        val fileName = "error_file.txt"
        val savePath = File.createTempFile("download_api_err_", ".txt").apply { deleteOnExit() }
        val apiErrorMessage = "403 Forbidden: Insufficient Permissions"

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setQ(any())).thenReturn(listMock)
        `when`(listMock.setSpaces(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenThrow(IOException(apiErrorMessage))

        val exception = assertThrows<IOException> {
            driveService.downloadFile(driveClient, fileName, savePath)
        }

        assertEquals(apiErrorMessage, exception.message)
        verify(filesMock, never()).get(any())
    }

    @Test
    fun `downloadFile - drive download api throws - propagates exception when executeAndDownloadTo fails`() {
        val fileName = "corrupt_stream.zip"
        val savePath = File.createTempFile("download_stream_err_", ".zip").apply { deleteOnExit() }
        val fileId = "valid-file-id-999"
        val downloadErrorMessage = "Connection reset by peer"

        val file1 = DriveFile().apply { id = fileId; name = fileName }
        val fileList = FileList().setFiles(listOf(file1))

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setQ(any())).thenReturn(listMock)
        `when`(listMock.setSpaces(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(fileList)

        `when`(filesMock.get(fileId)).thenReturn(getMock)
        `when`(getMock.executeAndDownloadTo(any())).thenThrow(IOException(downloadErrorMessage))

        val exception = assertThrows<IOException> {
            driveService.downloadFile(driveClient, fileName, savePath)
        }

        assertEquals(downloadErrorMessage, exception.message)
    }

    @Test
    fun `listFiles - happy path - returns formatted list of file names when files exist`() {
        val file1 = DriveFile().apply { id = "id-1"; name = "document.pdf" }
        val file2 = DriveFile().apply { id = "id-2"; name = "spreadsheet.xlsx" }
        val fileList = FileList().setFiles(listOf(file1, file2))

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setPageSize(10)).thenReturn(listMock)
        `when`(listMock.setFields("nextPageToken, files(id, name)")).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(true)).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(true)).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(fileList)

        val result = driveService.listFiles(driveClient)

        val expectedOutput = "Available Files: \ndocument.pdf\nspreadsheet.xlsx\n"
        assertEquals(expectedOutput, result)

        verify(listMock).setPageSize(10)
        verify(listMock).setFields("nextPageToken, files(id, name)")
        verify(listMock).setSupportsAllDrives(true)
        verify(listMock).setIncludeItemsFromAllDrives(true)
        verify(logger).info("Found {} files in Google Drive", any())
    }

    @Test
    fun `listFiles - empty list - returns 'Is null or empty' when file list is empty`() {
        val emptyFileList = FileList().setFiles(emptyList())

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setPageSize(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(emptyFileList)

        val result = driveService.listFiles(driveClient)

        assertEquals("Is null or empty.", result)
        verify(logger).info("No Google Drive files found.")
    }

    @Test
    fun `listFiles - null files property - returns 'Is null or empty' when files field is null`() {
        val nullFilesResult = FileList().setFiles(null)

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setPageSize(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenReturn(nullFilesResult)

        val result = driveService.listFiles(driveClient)

        assertEquals("Is null or empty.", result)
        verify(logger).info("No Google Drive files found.")
    }

    @Test
    fun `listFiles - drive api exception - propagates exception when execute fails`() {
        val apiErrorMessage = "401 Unauthorized"

        `when`(driveClient.files()).thenReturn(filesMock)
        `when`(filesMock.list()).thenReturn(listMock)
        `when`(listMock.setPageSize(any())).thenReturn(listMock)
        `when`(listMock.setFields(any())).thenReturn(listMock)
        `when`(listMock.setSupportsAllDrives(any())).thenReturn(listMock)
        `when`(listMock.setIncludeItemsFromAllDrives(any())).thenReturn(listMock)
        `when`(listMock.execute()).thenThrow(IOException(apiErrorMessage))

        val exception = assertThrows<IOException> {
            driveService.listFiles(driveClient)
        }

        assertEquals(apiErrorMessage, exception.message)
    }
    
}