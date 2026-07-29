package com.example.amie.data.remote.parser

/**
 * An immutable rendering snapshot state tracking layered application windows.
 *
 * @property windowRendering An index counter denoting the current stacked display layer tracking state.
 */
data class PhoneRendering(
    val windowRendering: Int,
)
