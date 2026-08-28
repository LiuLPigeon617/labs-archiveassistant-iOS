package com.lyihub.archiveassistant.domain

/**
 * Extracts the first complete JSON object from a model response.
 *
 * Models frequently wrap JSON in Markdown fences or prepend prose, so a tolerant scan is required.
 * This is a hand-written scanner rather than a parser for two reasons: it must tolerate trailing
 * prose after the object, and it must not fail on content inside strings that merely looks like
 * braces.
 *
 * @return the substring containing the balanced JSON object.
 * @throws IllegalArgumentException if no complete object is found.
 */
fun extractJsonObject(output: String): String {
  val trimmed = output.trim()
  val unfenced =
    if (trimmed.startsWith("```")) {
      trimmed
        .removePrefix("```")
        .removePrefix("json")
        .removePrefix("JSON")
        .trim()
        .removeSuffix("```")
        .trim()
    } else {
      trimmed
    }

  val start = unfenced.indexOf('{')
  require(start >= 0) { "No JSON object start found" }

  var depth = 0
  var inString = false
  var isEscaped = false
  for (index in start until unfenced.length) {
    val char = unfenced[index]
    if (isEscaped) {
      isEscaped = false
      continue
    }
    when {
      char == '\\' && inString -> isEscaped = true
      char == '"' -> inString = !inString
      !inString && char == '{' -> depth += 1
      !inString && char == '}' -> {
        depth -= 1
        if (depth == 0) return unfenced.substring(start, index + 1)
      }
    }
  }

  throw IllegalArgumentException("No complete JSON object found")
}
