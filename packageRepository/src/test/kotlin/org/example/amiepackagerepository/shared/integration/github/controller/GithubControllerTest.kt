package org.example.amiepackagerepository.shared.integration.github.controller

import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockMvcBuilder
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class GithubControllerTest {

    @Mock
    private lateinit var controller: GitHubController // Replace with your actual controller class name

    @Mock
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    private fun setUp(){
        mockMvc = MockMvcBuilder.standaloneSetup(controller).build()
    }

    @Test
    fun `getRepositoryFiles - GET list-github - returns 200OK status and files`() {
        val item1 = GithubItemDto(name = "file1.kt", path = "uploads/file1.kt", type = "file")
        val item2 = GithubItemDto(name = "file2.kt", path = "uploads/file2.kt", type = "file")
        val files = listOf(item1, item2)

        //I assume this is what stubbing for controllers looks like..

        mockMvc.perform(get("/list-github").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("script.kt"))
            .andExpect(jsonPath("$[0].path").value("uploads/john/script.kt"))
            .andExpect(jsonPath("$[0].type").value("file"))
            .andExpect(jsonPath("$[1].name").value("config.json"))
    }
    
}