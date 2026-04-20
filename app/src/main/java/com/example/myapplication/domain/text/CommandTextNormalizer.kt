package com.example.myapplication.domain.text

import java.text.Normalizer
import java.util.Locale

object CommandTextNormalizer {
    fun normalize(input: String): String {
        return Normalizer
            .normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
