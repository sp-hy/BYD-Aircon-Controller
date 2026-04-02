package com.sphy.airconcontroller.byd

internal data class BangcleTables(
    val invRound: ByteArray,
    val invXor: ByteArray,
    val invFirst: ByteArray,
    val round: ByteArray,
    val xor: ByteArray,
    val final: ByteArray,
    val permDecrypt: ByteArray,
    val permEncrypt: ByteArray
)

internal object BangcleWhiteBox {
    fun decryptCbc(tables: BangcleTables, data: ByteArray, iv: ByteArray): ByteArray {
        require(data.size % 16 == 0) { "Ciphertext must be 16-byte aligned" }
        require(iv.size == 16) { "IV must be 16 bytes" }
        val out = ByteArray(data.size)
        var prev = iv.copyOf()
        var off = 0
        while (off < data.size) {
            val block = data.copyOfRange(off, off + 16)
            val dec = decryptBlockAuth(tables, block)
            for (i in 0 until 16) dec[i] = (dec[i].toInt() xor prev[i].toInt()).toByte()
            System.arraycopy(dec, 0, out, off, 16)
            prev = block
            off += 16
        }
        return out
    }

    fun encryptCbc(tables: BangcleTables, data: ByteArray, iv: ByteArray): ByteArray {
        require(data.size % 16 == 0) { "Plaintext must be 16-byte aligned" }
        require(iv.size == 16) { "IV must be 16 bytes" }
        val out = ByteArray(data.size)
        var prev = iv.copyOf()
        var off = 0
        while (off < data.size) {
            val block = data.copyOfRange(off, off + 16)
            for (i in 0 until 16) block[i] = (block[i].toInt() xor prev[i].toInt()).toByte()
            val enc = encryptBlockAuth(tables, block)
            System.arraycopy(enc, 0, out, off, 16)
            prev = enc
            off += 16
        }
        return out
    }

    private fun prepareAesMatrix(input: ByteArray, output: ByteArray) {
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                output[col * 8 + row] = input[col + row * 4]
            }
        }
    }

    private fun decryptBlockAuth(tables: BangcleTables, block: ByteArray): ByteArray {
        val state = ByteArray(32)
        val temp64 = ByteArray(64)
        val tmp32 = ByteArray(32)
        val output = ByteArray(16)
        prepareAesMatrix(block, state)

        for (rnd in 9 downTo 1) {
            val lVar21 = rnd * 4
            var permPtr = 0
            for (i in 0 until 4) {
                val bVar3 = tables.permDecrypt[permPtr].toInt() and 0xFF
                val lVar16 = i * 8
                val base = i * 16
                for (j in 0 until 4) {
                    val uVar7 = (bVar3 + j) and 3
                    val byteVal = state[lVar16 + uVar7].toInt() and 0xFF
                    val idx = byteVal + (i + (lVar21 + uVar7) * 4) * 256
                    writeLe32(temp64, base + j * 4, readLe32(tables.invRound, idx * 4))
                }
                permPtr += 2
            }

            var iVar15 = 1
            for (lVar21Xor in 0 until 4) {
                var pbOffset = lVar21Xor
                for (lVar9Xor in 0 until 4) {
                    val local10 = temp64[pbOffset].toInt() and 0xFF
                    var uVar6 = local10 and 0xF
                    var uVar26 = local10 and 0xF0
                    val localF0 = temp64[pbOffset + 0x10].toInt() and 0xFF
                    val localF1 = temp64[pbOffset + 0x20].toInt() and 0xFF
                    val localF2 = temp64[pbOffset + 0x30].toInt() and 0xFF
                    val lVar2 = lVar9Xor * 0x18 + rnd * 0x60
                    var iVar25 = iVar15

                    for (k in 0 until 3) {
                        val b = when (k) {
                            0 -> localF0
                            1 -> localF1
                            else -> localF2
                        }
                        val uVar1 = (b shl 4) and 0xFF
                        val uVar27 = uVar6 or uVar1
                        uVar26 = ((uVar26 shr 4) or ((b shr 4) shl 4)) and 0xFF
                        val idx1 = (lVar2 + (iVar25 - 1)) * 0x100 + uVar27
                        uVar6 = tables.invXor[idx1].toInt() and 0xF
                        val idx2 = (lVar2 + iVar25) * 0x100 + uVar26
                        val bNew = tables.invXor[idx2].toInt() and 0xFF
                        uVar26 = (bNew and 0xF) shl 4
                        iVar25 += 2
                    }
                    state[lVar9Xor + lVar21Xor * 8] = (uVar26 or uVar6).toByte()
                    pbOffset += 4
                }
                iVar15 += 6
            }
        }

        System.arraycopy(state, 0, tmp32, 0, 32)
        var uVar8 = 1
        var uVar10 = 3
        var uVar12 = 2
        for (row in 0 until 4) {
            val idx0 = (tmp32[row].toInt() and 0xFF) + row * 0x400
            state[row] = tables.invFirst[idx0]
            val row1 = uVar10 and 3
            val idx1 = (tmp32[8 + row1].toInt() and 0xFF) + row1 * 0x400 + 0x100
            state[8 + row] = tables.invFirst[idx1]
            val row2 = uVar12 and 3
            val idx2 = (tmp32[0x10 + row2].toInt() and 0xFF) + row2 * 0x400 + 0x200
            state[0x10 + row] = tables.invFirst[idx2]
            val row3 = uVar8 and 3
            val idx3 = (tmp32[0x18 + row3].toInt() and 0xFF) + row3 * 0x400 + 0x300
            state[0x18 + row] = tables.invFirst[idx3]
            uVar8 += 1
            uVar10 += 1
            uVar12 += 1
        }

        for (col in 0 until 4) {
            for (row in 0 until 4) {
                output[col + row * 4] = state[col * 8 + row]
            }
        }
        return output
    }

    private fun encryptBlockAuth(tables: BangcleTables, block: ByteArray): ByteArray {
        val state = ByteArray(32)
        val temp64 = ByteArray(64)
        val tmp32 = ByteArray(32)
        val output = ByteArray(16)
        prepareAesMatrix(block, state)

        for (rnd in 0 until 9) {
            val lVar21 = rnd * 4
            var permPtr = 0
            for (i in 0 until 4) {
                val bVar4 = tables.permEncrypt[permPtr].toInt() and 0xFF
                val lVar16 = i * 8
                val base = i * 16
                for (j in 0 until 4) {
                    val uVar8 = (bVar4 + j) and 3
                    val byteVal = state[lVar16 + uVar8].toInt() and 0xFF
                    val idx = byteVal + (i + (lVar21 + uVar8) * 4) * 256
                    writeLe32(temp64, base + j * 4, readLe32(tables.round, idx * 4))
                }
                permPtr += 2
            }

            var iVar16 = 1
            for (lVar22 in 0 until 4) {
                var pbOffset = lVar22
                for (lVar10 in 0 until 4) {
                    val local10 = temp64[pbOffset].toInt() and 0xFF
                    var uVar7 = local10 and 0xF
                    var uVar26 = local10 and 0xF0
                    val localF0 = temp64[pbOffset + 0x10].toInt() and 0xFF
                    val localF1 = temp64[pbOffset + 0x20].toInt() and 0xFF
                    val localF2 = temp64[pbOffset + 0x30].toInt() and 0xFF
                    val lVar2 = lVar10 * 0x18 + rnd * 0x60
                    var iVar25 = iVar16

                    for (k in 0 until 3) {
                        val b = when (k) {
                            0 -> localF0
                            1 -> localF1
                            else -> localF2
                        }
                        val uVar1 = (b shl 4) and 0xFF
                        val uVar27 = uVar7 or uVar1
                        uVar26 = ((uVar26 shr 4) or ((b shr 4) shl 4)) and 0xFF
                        val idx1 = (lVar2 + (iVar25 - 1)) * 0x100 + uVar27
                        uVar7 = tables.xor[idx1].toInt() and 0xF
                        val idx2 = (lVar2 + iVar25) * 0x100 + uVar26
                        val bNew = tables.xor[idx2].toInt() and 0xFF
                        uVar26 = (bNew and 0xF) shl 4
                        iVar25 += 2
                    }
                    state[lVar10 + lVar22 * 8] = (uVar26 or uVar7).toByte()
                    pbOffset += 4
                }
                iVar16 += 6
            }
        }

        System.arraycopy(state, 0, tmp32, 0, 32)
        var uVar13 = 3
        var uVar9 = 2
        var uVar11 = 1
        var uVar8Enc = 0
        for (row in 0 until 4) {
            val row0 = (uVar8Enc + row) and 3
            state[row] = tables.final[(tmp32[row0].toInt() and 0xFF) + row0 * 0x400]
            val row1 = (uVar11 + row) and 3
            state[8 + row] = tables.final[(tmp32[8 + row1].toInt() and 0xFF) + row1 * 0x400 + 0x100]
            val row2 = (uVar9 + row) and 3
            state[0x10 + row] = tables.final[(tmp32[0x10 + row2].toInt() and 0xFF) + row2 * 0x400 + 0x200]
            val row3 = (uVar13 + row) and 3
            state[0x18 + row] = tables.final[(tmp32[0x18 + row3].toInt() and 0xFF) + row3 * 0x400 + 0x300]
        }

        for (col in 0 until 4) {
            for (row in 0 until 4) {
                output[col + row * 4] = state[col * 8 + row]
            }
        }
        return output
    }

    private fun readLe32(buf: ByteArray, off: Int): Int {
        return (buf[off].toInt() and 0xFF) or
            ((buf[off + 1].toInt() and 0xFF) shl 8) or
            ((buf[off + 2].toInt() and 0xFF) shl 16) or
            ((buf[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeLe32(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value and 0xFF).toByte()
        buf[off + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
