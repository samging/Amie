package org.example.amiepackagerepository.shared.integration.github.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Internal DTO for GitHub API response items.
 */
data class GithubSearchResponseDto(
    @JsonProperty("total_count") val totalCount: Int,
    @JsonProperty("incomplete_results") val incompleteResults: Boolean,
    val items: List<GithubSearchItemDto>
)
