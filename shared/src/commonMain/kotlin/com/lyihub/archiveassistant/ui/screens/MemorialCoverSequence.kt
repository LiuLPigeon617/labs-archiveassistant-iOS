package com.lyihub.archiveassistant.ui.screens

import com.lyihub.archiveassistant.domain.KnowledgeItem
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

internal object MemorialCoverSequence {
  fun wheelResources(
    coverResources: List<Int>,
    itemCount: Int,
    duplicateGuard: Int,
    seed: Int,
  ): List<Int> {
    if (coverResources.isEmpty()) return emptyList()
    val uniqueResources = coverResources.distinct()
    val guard = min(duplicateGuard, (uniqueResources.size - 1).coerceAtLeast(0))
    if (guard == 0) return List(itemCount) { coverResources[it % coverResources.size] }

    val shuffled = uniqueResources.shuffled(Random(seed))
    val step = coprimeStep(shuffled.size, guard + 1)
    val sequence = List(itemCount) { index -> shuffled[(index * step) % shuffled.size] }
    if (isCircularSequenceValid(sequence, guard)) return sequence

    return greedyCircularFallback(shuffled, itemCount, guard)
  }

  fun articleCoverOffset(item: KnowledgeItem, coverPoolSize: Int): Int {
    if (coverPoolSize <= 1) return 0
    val seed = "${item.id}|${item.title}".hashCode()
    return 1 + positiveMod(seed, coverPoolSize - 1)
  }

  fun texturePoolIndex(sequenceIndex: Int, generatedTextureCount: Int): Int {
    return positiveMod(sequenceIndex, generatedTextureCount)
  }

  private fun greedyCircularFallback(
    resources: List<Int>,
    itemCount: Int,
    guard: Int,
  ): List<Int> {
    val sequence = mutableListOf<Int>()
    repeat(itemCount) { index ->
      val candidate =
        resources
          .sortedBy { resource -> sequence.count { it == resource } }
          .firstOrNull { resource ->
            sequence.takeLast(guard).none { recent -> recent == resource }
          } ?: resources[index % resources.size]
      sequence += candidate
    }
    repeat(itemCount * resources.size) {
      if (isCircularSequenceValid(sequence, guard)) {
        return sequence
      }
      val conflictIndex =
        sequence.indices.firstOrNull { index ->
          (1..guard).any { distance ->
            sequence[index] == sequence[(index + distance) % sequence.size]
          }
        } ?: return sequence
      val swapIndex =
        sequence.indices.firstOrNull { index ->
          index != conflictIndex &&
            canSwapWithoutNearDuplicate(sequence, conflictIndex, index, guard)
        } ?: return sequence
      val tmp = sequence[conflictIndex]
      sequence[conflictIndex] = sequence[swapIndex]
      sequence[swapIndex] = tmp
    }
    return sequence
  }

  private fun coprimeStep(size: Int, preferred: Int): Int {
    if (size <= 1) return 1
    for (step in preferred.coerceAtLeast(1) until size) {
      if (gcd(size, step) == 1) return step
    }
    return 1
  }

  private tailrec fun gcd(a: Int, b: Int): Int {
    return if (b == 0) abs(a) else gcd(b, a % b)
  }

  private fun canSwapWithoutNearDuplicate(
    sequence: List<Int>,
    firstIndex: Int,
    secondIndex: Int,
    guard: Int,
  ): Boolean {
    val mutable = sequence.toMutableList()
    val tmp = mutable[firstIndex]
    mutable[firstIndex] = mutable[secondIndex]
    mutable[secondIndex] = tmp
    return isCircularSequenceValidAt(mutable, firstIndex, guard) &&
      isCircularSequenceValidAt(mutable, secondIndex, guard)
  }

  private fun isCircularSequenceValid(sequence: List<Int>, guard: Int): Boolean {
    if (sequence.isEmpty()) return true
    return sequence.indices.all { index ->
      isCircularSequenceValidAt(sequence, index, guard)
    }
  }

  private fun isCircularSequenceValidAt(sequence: List<Int>, index: Int, guard: Int): Boolean {
    if (sequence.isEmpty()) return true
    return (1..guard).all { distance ->
      sequence[index] != sequence[(index + distance) % sequence.size] &&
        sequence[index] != sequence[(index - distance + sequence.size) % sequence.size]
    }
  }
}
