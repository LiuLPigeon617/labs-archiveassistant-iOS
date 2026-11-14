#!/usr/bin/env swift
//
// Screenshot sanity check for the iOS simulator smoke test.
//
// A screenshot of a blank, all-white or all-black screen is still a perfectly valid PNG, so
// "the file exists" proves nothing about whether the app actually rendered. This reports basic pixel
// statistics and fails only on an image that is clearly uniform.
//
// The check is deliberately weak. It is not a visual regression test - it cannot tell a correct
// layout from a broken one. It answers exactly one question: did anything get drawn at all?
//
// Usage:   swift check-screenshot.swift <png> [minDistinctColors]
// Exit:    0 = content was drawn, 1 = image is uniform/blank, 2 = bad usage or undecodable file.

import CoreGraphics
import Foundation
import ImageIO

let args = CommandLine.arguments
guard args.count >= 2 else {
  FileHandle.standardError.write(Data("usage: check-screenshot.swift <png> [minDistinctColors]\n".utf8))
  exit(2)
}
let path = args[1]
let minDistinct = args.count >= 3 ? (Int(args[2]) ?? 50) : 50

guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
  print("FAIL  \(path): could not decode as an image")
  exit(2)
}

let width = image.width
let height = image.height
// Round the row stride up to 16 bytes: an unaligned bytesPerRow can make CGContext creation fail, and
// a context that never draws leaves the buffer at all-zero, which would then be reported as a flat
// black screen - a false failure in the very check that exists to avoid false results.
let bytesPerRow = (width * 4 + 15) & ~15

var buffer = [UInt8](repeating: 0, count: bytesPerRow * height)
var didDraw = false
buffer.withUnsafeMutableBytes { raw in
  guard let context = CGContext(
    data: raw.baseAddress,
    width: width,
    height: height,
    bitsPerComponent: 8,
    bytesPerRow: bytesPerRow,
    space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
  ) else { return }
  context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
  didDraw = true
}

guard didDraw else {
  print("FAIL  \(path): could not create a drawing context for the image")
  exit(2)
}

var seen = Set<UInt32>()
var totalLuminance = 0.0
var samples = 0
var darkest = 255.0
var brightest = 0.0

// Sampling every 4th pixel on both axes is ample for a blank-screen check and keeps this fast on the
// large (2x/3x) simulator screenshots.
for y in stride(from: 0, to: height, by: 4) {
  let row = y * bytesPerRow
  for x in stride(from: 0, to: width, by: 4) {
    let offset = row + x * 4
    let r = buffer[offset]
    let g = buffer[offset + 1]
    let b = buffer[offset + 2]

    let red = Double(r)
    let green = Double(g)
    let blue = Double(b)
    // Quantise to 5 bits per channel. Antialiasing on text edges would otherwise inflate the
    // distinct-colour count of an otherwise flat background on its own.
    let key = UInt32(r >> 3) << 10 | UInt32(g >> 3) << 5 | UInt32(b >> 3)
    seen.insert(key)

    let luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    totalLuminance += luminance
    darkest = min(darkest, luminance)
    brightest = max(brightest, luminance)
    samples += 1
  }
}

let meanLuminance = samples > 0 ? totalLuminance / Double(samples) : 0
print(String(
  format: "%@: %dx%d, distinctColours=%d, meanLuminance=%.1f, luminanceRange=%.1f..%.1f",
  (path as NSString).lastPathComponent, width, height, seen.count, meanLuminance, darkest, brightest
))

if seen.count < minDistinct {
  print("FAIL  only \(seen.count) distinct colours (minimum \(minDistinct)): nothing was drawn, "
    + "or the screen is a flat placeholder.")
  exit(1)
}

print("OK    content was drawn.")
