package com.wanderwk.d3saveeditor.core

/**
 * Minimal, permissive Protobuf wire-format reader/writer. The save files
 * are never validated against a real .proto schema (Blizzard's is not
 * public) -- like the reference Python tool, we just walk fields generically
 * by (field_number, wire_type) and patch the ones we understand, leaving
 * everything else byte-identical. This guarantees round-tripping unknown
 * fields safely.
 */
class ProtoFormatException(message: String) : Exception(message)

object Varint {
    /** Returns (value, newOffset). */
    fun read(data: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (true) {
            if (pos >= data.size) throw ProtoFormatException("Varint truncado")
            val b = data[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to pos
    }

    fun write(value: Long): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (v and 0x7FL.inv() != 0L && v > 0x7F) {
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        out.add((v and 0x7F).toByte())
        return out.toByteArray()
    }
}

/** wireType: 0=varint, 1=fixed64, 2=length-delimited, 5=fixed32.
 * value is Long for wt 0, ByteArray for wt 1/2/5. */
data class PField(val fieldNumber: Int, val wireType: Int, val value: Any)

fun PField.longValue(): Long = value as Long
fun PField.bytesValue(): ByteArray = value as ByteArray

fun parseFields(data: ByteArray): MutableList<PField> {
    val fields = ArrayList<PField>()
    var offset = 0
    try {
        while (offset < data.size) {
            val (header, afterHeader) = Varint.read(data, offset)
            offset = afterHeader
            val fn = (header ushr 3).toInt()
            val wt = (header and 0x07).toInt()
            when (wt) {
                0 -> {
                    val (v, next) = Varint.read(data, offset)
                    fields.add(PField(fn, 0, v))
                    offset = next
                }
                1 -> {
                    if (offset + 8 > data.size) throw ProtoFormatException("fixed64 truncado")
                    fields.add(PField(fn, 1, data.copyOfRange(offset, offset + 8)))
                    offset += 8
                }
                2 -> {
                    val (len, next) = Varint.read(data, offset)
                    offset = next
                    val length = len.toInt()
                    if (offset + length > data.size || length < 0) {
                        throw ProtoFormatException("length-delimited truncado")
                    }
                    fields.add(PField(fn, 2, data.copyOfRange(offset, offset + length)))
                    offset += length
                }
                5 -> {
                    if (offset + 4 > data.size) throw ProtoFormatException("fixed32 truncado")
                    fields.add(PField(fn, 5, data.copyOfRange(offset, offset + 4)))
                    offset += 4
                }
                else -> throw ProtoFormatException("Wire type desconhecido: $wt")
            }
        }
    } catch (_: ProtoFormatException) {
        // Mirrors the Python tool: on truncation/garbage, stop and return
        // whatever was parsed so far rather than failing the whole read.
    }
    return fields
}

fun serializeFields(fields: List<PField>): ByteArray {
    val buf = java.io.ByteArrayOutputStream()
    for (f in fields) {
        val header = (f.fieldNumber.toLong() shl 3) or f.wireType.toLong()
        buf.write(Varint.write(header))
        when (f.wireType) {
            0 -> buf.write(Varint.write(f.longValue()))
            1, 5 -> buf.write(f.bytesValue())
            2 -> {
                val payload = f.bytesValue()
                buf.write(Varint.write(payload.size.toLong()))
                buf.write(payload)
            }
        }
    }
    return buf.toByteArray()
}

fun encodeVarintField(fieldNumber: Int, value: Long): ByteArray {
    val buf = java.io.ByteArrayOutputStream()
    buf.write(Varint.write((fieldNumber.toLong() shl 3) or 0L))
    buf.write(Varint.write(value))
    return buf.toByteArray()
}

fun encodeLengthDelimitedField(fieldNumber: Int, payload: ByteArray): ByteArray {
    val buf = java.io.ByteArrayOutputStream()
    buf.write(Varint.write((fieldNumber.toLong() shl 3) or 2L))
    buf.write(Varint.write(payload.size.toLong()))
    buf.write(payload)
    return buf.toByteArray()
}
