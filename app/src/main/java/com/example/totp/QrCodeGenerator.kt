package com.example.totp

import java.nio.charset.StandardCharsets

/**
 * Pure local ISO/IEC 18004 Byte-mode QR Code generator (Versions 1..10, ECC Level M).
 * Zero network calls, zero external dependencies — generates scannable matrices for otpauth:// URIs.
 */
object QrCodeGenerator {

    data class QrMatrix(val size: Int, val modules: Array<BooleanArray>)

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataCodewordsGroup1: Int,
        val numBlocksGroup2: Int = 0,
        val dataCodewordsGroup2: Int = 0,
        val alignmentCenters: IntArray
    ) {
        val totalDataCodewords: Int
            get() = numBlocksGroup1 * dataCodewordsGroup1 + numBlocksGroup2 * dataCodewordsGroup2
    }

    // ECC Level M specifications for Versions 1..10 (up to 213 bytes — plenty for otpauth URIs)
    private val VERSION_SPECS = listOf(
        VersionSpec(1, 26, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 44, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 70, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 100, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 134, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 172, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 196, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    private val VERSION_INFO_BITS = mapOf(
        7 to 0x07C94,
        8 to 0x085BC,
        9 to 0x09A99,
        10 to 0x0A4D3
    )

    fun encode(text: String): QrMatrix {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val spec = VERSION_SPECS.firstOrNull { candidate ->
            val countBits = if (candidate.version <= 9) 8 else 16
            val capacityBytes = (candidate.totalDataCodewords * 8 - 4 - countBits) / 8
            bytes.size <= capacityBytes
        } ?: VERSION_SPECS.last()

        val dataCodewords = buildDataCodewords(bytes, spec)
        val interleaved = buildInterleavedCodewords(dataCodewords, spec)
        return buildMatrix(spec, interleaved)
    }

    private fun buildDataCodewords(data: ByteArray, spec: VersionSpec): ByteArray {
        val bits = ArrayList<Int>(spec.totalDataCodewords * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator (0100)
        appendBits(0b0100, 4)
        val countBits = if (spec.version <= 9) 8 else 16
        appendBits(data.size, countBits)
        for (b in data) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val maxBits = spec.totalDataCodewords * 8
        val terminator = minOf(4, maxBits - bits.size)
        appendBits(0, terminator)
        while (bits.size % 8 != 0) bits.add(0)

        val result = ByteArray(spec.totalDataCodewords)
        var byteIdx = 0
        var i = 0
        while (i < bits.size && byteIdx < result.size) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i + b]
            }
            result[byteIdx++] = v.toByte()
            i += 8
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (byteIdx < result.size) {
            result[byteIdx++] = padBytes[padIdx % 2].toByte()
            padIdx++
        }
        return result
    }

    private fun buildInterleavedCodewords(data: ByteArray, spec: VersionSpec): ByteArray {
        val dataBlocks = ArrayList<ByteArray>()
        val ecBlocks = ArrayList<ByteArray>()
        val generator = reedSolomonGenerator(spec.ecCodewordsPerBlock)

        var offset = 0
        repeat(spec.numBlocksGroup1) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup1)
            offset += spec.dataCodewordsGroup1
            dataBlocks.add(block)
            ecBlocks.add(reedSolomonCompute(block, generator))
        }
        repeat(spec.numBlocksGroup2) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup2)
            offset += spec.dataCodewordsGroup2
            dataBlocks.add(block)
            ecBlocks.add(reedSolomonCompute(block, generator))
        }

        val out = ByteArray(spec.totalCodewords)
        var pos = 0
        val maxDataLen = maxOf(spec.dataCodewordsGroup1, spec.dataCodewordsGroup2)
        for (col in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (col < block.size) out[pos++] = block[col]
            }
        }
        for (col in 0 until spec.ecCodewordsPerBlock) {
            for (ec in ecBlocks) {
                out[pos++] = ec[col]
            }
        }
        return out
    }

    private fun buildMatrix(spec: VersionSpec, codewords: ByteArray): QrMatrix {
        val size = spec.version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunc(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        // Finder patterns + separators
        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until size || c !in 0 until size) continue
                    val dark = dr in 0..6 && dc in 0..6 && (
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                    )
                    setFunc(r, c, dark)
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            setFunc(6, i, i % 2 == 0)
            setFunc(i, 6, i % 2 == 0)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (r in centers) {
            for (c in centers) {
                if (isFunction[r][c]) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                        setFunc(r + dr, c + dc, dist != 1)
                    }
                }
            }
        }

        // Dark module
        setFunc(size - 8, 8, true)

        // Reserve format info areas
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in size - 8 until size) {
            isFunction[8][i] = true
            isFunction[i][8] = true
        }

        // Version info (versions >= 7)
        VERSION_INFO_BITS[spec.version]?.let { bits ->
            for (i in 0 until 18) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunc(a, b, bit)
                setFunc(b, a, bit)
            }
        }

        // Place data bits in zigzag
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var col = size - 1
        var upward = true
        while (col >= 1) {
            if (col == 6) col--
            for (step in 0 until size) {
                val row = if (upward) size - 1 - step else step
                for (dc in 0..1) {
                    val c = col - dc
                    if (!isFunction[row][c]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx ushr 3].toInt() ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[row][c] = dark
                        bitIdx++
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        // Evaluate all 8 mask patterns and pick lowest penalty
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = modules
        for (mask in 0..7) {
            val candidate = Array(size) { r -> modules[r].copyOf() }
            applyMask(candidate, isFunction, mask)
            writeFormatBits(candidate, mask)
            val penalty = computePenalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }
        // bestMask already applied in bestGrid
        val _unused = bestMask
        return QrMatrix(size, bestGrid)
    }

    private fun applyMask(grid: Array<BooleanArray>, isFunction: Array<BooleanArray>, mask: Int) {
        val size = grid.size
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (isFunction[r][c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> ((r / 2) + (c / 3)) % 2 == 0
                    5 -> ((r * c) % 2) + ((r * c) % 3) == 0
                    6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
                    7 -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
                    else -> false
                }
                if (invert) grid[r][c] = !grid[r][c]
            }
        }
    }

    private fun writeFormatBits(grid: Array<BooleanArray>, mask: Int) {
        val size = grid.size
        // ECC Level M format indicator = 00
        val data = (0b00 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) grid[8][i] = ((bits ushr (14 - i)) and 1) != 0
        grid[8][7] = ((bits ushr 8) and 1) != 0
        grid[8][8] = ((bits ushr 7) and 1) != 0
        grid[7][8] = ((bits ushr 6) and 1) != 0
        for (i in 9..14) grid[14 - i][8] = ((bits ushr (14 - i)) and 1) != 0

        for (i in 0..7) grid[8][size - 1 - i] = ((bits ushr i) and 1) != 0
        for (i in 8..14) grid[size - 15 + i][8] = ((bits ushr i) and 1) != 0
    }

    private fun computePenalty(grid: Array<BooleanArray>): Int {
        val size = grid.size
        var penalty = 0
        // Adjacent modules in row/col
        for (r in 0 until size) {
            var runLen = 1
            for (c in 1 until size) {
                if (grid[r][c] == grid[r][c - 1]) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runLen = 1
                }
            }
        }
        for (c in 0 until size) {
            var runLen = 1
            for (r in 1 until size) {
                if (grid[r][c] == grid[r - 1][c]) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runLen = 1
                }
            }
        }
        // 2x2 blocks
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val v = grid[r][c]
                if (grid[r][c + 1] == v && grid[r + 1][c] == v && grid[r + 1][c + 1] == v) {
                    penalty += 3
                }
            }
        }
        return penalty
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        var a = x
        var b = y
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            if (((b ushr i) and 1) != 0) z = z xor a
        }
        return z
    }

    private fun reedSolomonGenerator(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) {
                    result[j] = ((result[j].toInt() and 0xFF) xor (result[j + 1].toInt() and 0xFF)).toByte()
                }
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonCompute(data: ByteArray, generator: ByteArray): ByteArray {
        val result = ByteArray(generator.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor (result[0].toInt() and 0xFF)
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (j in generator.indices) {
                result[j] = ((result[j].toInt() and 0xFF) xor gfMultiply(generator[j].toInt() and 0xFF, factor)).toByte()
            }
        }
        return result
    }
}
