package org.example.amiepackagerepository.website

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.example.amiepackagerepository.TestcontainersConfiguration
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class UserControllerTest {

    @Test
    fun `context loads`() {
        // Boilerplate placeholder test for UserController
    }
}
