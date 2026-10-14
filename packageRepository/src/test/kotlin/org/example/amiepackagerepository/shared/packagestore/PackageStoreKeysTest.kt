package org.example.amiepackagerepository.shared.packagestore

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The key rules both services agree on: everything lives below `uploads/` and nothing may escape it. */
class PackageStoreKeysTest {
    @ParameterizedTest
    @ValueSource(strings = ["uploads/sam/jar/echo.jar", "uploads/sam/README.md", "uploads/sam/state/devices.json", "uploads/tool.c", "uploads/sam/onnx/my model.onnx"])
    fun `keys below uploads are allowed`(key: String) {
        assertTrue(PackageStoreClient.isAllowedKey(key), key)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "uploads/", "uploads", "releases/amie.dmg", "/uploads/sam/x", "uploads//x", "uploads/sam/", "uploads/../x", "uploads/sam/./x", "uploads/sam/../tom/x", "repositoryInformations"])
    fun `keys outside uploads or with empty or dot segments are refused`(key: String) {
        assertFalse(PackageStoreClient.isAllowedKey(key), key)
    }
}
