package org.example.amiepackagerepository.shared.integration.github.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data Transfer Object for the top-level response from the GitHub Search API.
 *
 * @property totalCount The total number of items matching the search query.
 * @property incompleteResults Indicates if the search results are truncated.
 * @property items The list of [GithubSearchItemDto] matching the query.
 */
data class GithubSearchResponseDto(
    @JsonProperty("total_count") val totalCount: Int,
    @JsonProperty("incomplete_results") val incompleteResults: Boolean,
    val items: List<GithubSearchItemDto>
)
