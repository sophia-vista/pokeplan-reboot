package app.pokeplan.ui.updates

/**
 * Rebuilds the new APK from the one installed on the phone, using a patch from `tools/update/make_patch.py`.
 *
 * An APK is a ZIP; an update leaves almost every entry (sprites, cries, maps) byte for byte the same, so the patch
 * lists each unchanged entry as a copy from the installed file and carries only the changed entries (mostly dex), the
 * APK signing block and the central directory. Rebuilding the new, signed APK exactly means the app never needs a
 * signing key, and the result can be checked against the release manifest's size and SHA-256 before it is installed.
 * A patch that does not apply (a different installed build, a damaged download) is dropped and the full APK is fetched.
 *
 * The patch is the gzip-decoded payload written by that script; the platform side gunzips it and checks the SHA-256.
 */
internal sealed interface PatchOp {
    class Copy(val offset: Long, val length: Int) : PatchOp
    class Literal(val offset: Int, val length: Int) : PatchOp
}

class UpdatePatchPlan internal constructor(
    private val patch: ByteArray,
    /** Size of the APK the patch rebuilds. */
    val targetSize: Long,
    /** SHA-256 the rebuilt APK should have, 32 bytes. */
    val targetSha256: ByteArray,
    private val ops: List<PatchOp>,
) {
    /** The target SHA-256 in lowercase hex, to compare with the release manifest. */
    val targetSha256Hex: String
        get() = targetSha256.joinToString("") { ((it.toInt() and 0xff) + 0x100).toString(16).substring(1) }

    /**
     * Writes the rebuilt APK. [readInstalled] returns exactly [length] bytes of the installed APK from [offset], or
     * null when it cannot; [write] receives bytes to append (a slice of the patch for a literal, or a fresh array for a
     * copy). [onProgress] gets 0 to 100, at most 101 times. Returns false as soon as an op cannot be carried out.
     */
    fun apply(
        readInstalled: (offset: Long, length: Int) -> ByteArray?,
        write: (bytes: ByteArray, offset: Int, length: Int) -> Unit,
        onProgress: (Int) -> Unit = {},
    ): Boolean {
        var written = 0L
        var reported = -1
        for (op in ops) {
            when (op) {
                is PatchOp.Copy -> {
                    val bytes = readInstalled(op.offset, op.length) ?: return false
                    if (bytes.size != op.length) return false
                    write(bytes, 0, op.length)
                }
                is PatchOp.Literal -> write(patch, op.offset, op.length)
            }
            written += when (op) {
                is PatchOp.Copy -> op.length.toLong()
                is PatchOp.Literal -> op.length.toLong()
            }
            val percent = if (targetSize > 0) ((written * 100) / targetSize).toInt().coerceIn(0, 100) else 0
            if (percent != reported) {
                reported = percent
                onProgress(percent)
            }
        }
        return written == targetSize
    }
}

private const val PATCH_MAGIC = "POKEPATCH1\n"

/** A patch larger than this is not one of ours (the biggest seen is about 20 MB gzipped, well under 64 MB decoded). */
const val MAX_PATCH_BYTES = 64 * 1024 * 1024

/**
 * Reads the gzip-decoded patch. [installedSize] is the installed APK's length, used to refuse a copy that runs past it.
 * Returns null when the patch is not one this build understands, so the caller falls back to the full download.
 */
fun parseUpdatePatch(patch: ByteArray, installedSize: Long): UpdatePatchPlan? {
    if (patch.size < PATCH_MAGIC.length + 8 + 32 + 4) return null
    if (!patch.copyOfRange(0, PATCH_MAGIC.length).contentEquals(PATCH_MAGIC.encodeToByteArray())) return null
    var at = PATCH_MAGIC.length
    fun u32(): Long? {
        if (at + 4 > patch.size) return null
        var value = 0L
        for (i in 0 until 4) value = (value shl 8) or (patch[at + i].toLong() and 0xff)
        at += 4
        return value
    }
    fun u64(): Long? {
        if (at + 8 > patch.size) return null
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (patch[at + i].toLong() and 0xff)
        at += 8
        return value
    }
    val targetSize = u64() ?: return null
    if (targetSize <= 0 || targetSize > 4L * 1024 * 1024 * 1024) return null
    if (at + 32 > patch.size) return null
    val targetSha = patch.copyOfRange(at, at + 32)
    at += 32
    val count = u32() ?: return null
    if (count <= 0 || count > 1_000_000) return null
    val ops = ArrayList<PatchOp>(count.toInt())
    var total = 0L
    repeat(count.toInt()) {
        if (at + 1 > patch.size) return null
        val kind = patch[at].toInt() and 0xff
        at += 1
        when (kind) {
            1 -> {
                val offset = u64() ?: return null
                val length = u32() ?: return null
                if (length <= 0 || offset < 0 || length > installedSize || offset > installedSize - length) return null
                ops += PatchOp.Copy(offset, length.toInt())
                total += length
            }
            2 -> {
                val length = u32() ?: return null
                if (length <= 0 || at + length > patch.size) return null
                ops += PatchOp.Literal(at, length.toInt())
                at += length.toInt()
                total += length
            }
            else -> return null
        }
        if (total > targetSize) return null
    }
    if (total != targetSize || at != patch.size) return null
    return UpdatePatchPlan(patch, targetSize, targetSha, ops)
}
