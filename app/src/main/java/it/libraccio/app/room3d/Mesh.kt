package it.libraccio.app.room3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesh con vertici interlacciati: posizione(3) normale(3) uv(2) faccia(1).
 * La "faccia" serve ai libri: 0 dorso, 1 copertina, 2 retro, 3..5 pagine; 6 = generica.
 */
class Mesh(vertices: FloatArray, indices: ShortArray) {
    private val vao: Int
    val count = indices.size

    init {
        val ids = IntArray(3)
        GLES30.glGenVertexArrays(1, ids, 0)
        GLES30.glGenBuffers(2, ids, 1)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)

        val vb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)

        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[2])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, ib, GLES30.GL_STATIC_DRAW)

        val stride = 9 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 32)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_SHORT, 0)
    }
}

private class MeshData {
    val v = ArrayList<Float>()
    val i = ArrayList<Short>()
    var n = 0

    fun vert(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float, u: Float, vv: Float, face: Float) {
        v.add(px); v.add(py); v.add(pz); v.add(nx); v.add(ny); v.add(nz); v.add(u); v.add(vv); v.add(face)
        n++
    }

    fun tri(a: Int, b: Int, c: Int) { i.add(a.toShort()); i.add(b.toShort()); i.add(c.toShort()) }

    fun build() = Mesh(v.toFloatArray(), i.toShortArray())
}

object Meshes {
    /** Cubo unitario da (0,0,0) a (1,1,1). */
    fun box(): Mesh {
        val d = MeshData()
        fun face(
            n: FloatArray,
            c: Array<FloatArray>,
            faceId: Float,
        ) {
            val base = d.n
            val uvs = arrayOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 1f), floatArrayOf(1f, 0f), floatArrayOf(0f, 0f))
            for (k in 0 until 4) d.vert(c[k][0], c[k][1], c[k][2], n[0], n[1], n[2], uvs[k][0], uvs[k][1], faceId)
            d.tri(base, base + 1, base + 2)
            d.tri(base, base + 2, base + 3)
        }
        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        // +z: dorso (u = x, v = 1 - y)
        face(floatArrayOf(0f, 0f, 1f), arrayOf(p(0f, 0f, 1f), p(1f, 0f, 1f), p(1f, 1f, 1f), p(0f, 1f, 1f)), 0f)
        // +x: copertina (u = 1 - z)
        face(floatArrayOf(1f, 0f, 0f), arrayOf(p(1f, 0f, 1f), p(1f, 0f, 0f), p(1f, 1f, 0f), p(1f, 1f, 1f)), 1f)
        // -x: retro (u = z)
        face(floatArrayOf(-1f, 0f, 0f), arrayOf(p(0f, 0f, 0f), p(0f, 0f, 1f), p(0f, 1f, 1f), p(0f, 1f, 0f)), 2f)
        // -z: taglio delle pagine (u = 1 - x)
        face(floatArrayOf(0f, 0f, -1f), arrayOf(p(1f, 0f, 0f), p(0f, 0f, 0f), p(0f, 1f, 0f), p(1f, 1f, 0f)), 3f)
        // +y: testa (u = x, v = z)
        face(floatArrayOf(0f, 1f, 0f), arrayOf(p(0f, 1f, 1f), p(1f, 1f, 1f), p(1f, 1f, 0f), p(0f, 1f, 0f)), 4f)
        // -y: piede
        face(floatArrayOf(0f, -1f, 0f), arrayOf(p(0f, 0f, 0f), p(1f, 0f, 0f), p(1f, 0f, 1f), p(0f, 0f, 1f)), 5f)
        return d.build()
    }

    /** Cilindro (o tronco di cono) alto 1 sull'asse y, raggio di base 1, raggio in cima [topRadius]. */
    fun cylinder(segments: Int = 28, topRadius: Float = 1f, caps: Boolean = true): Mesh {
        val d = MeshData()
        val slope = 1f - topRadius
        for (s in 0..segments) {
            val a = (2 * PI * s / segments).toFloat()
            val cx = cos(a)
            val cz = sin(a)
            val len = sqrt(1f + slope * slope)
            val nx = cx / len
            val ny = slope / len
            val nz = cz / len
            d.vert(cx, 0f, cz, nx, ny, nz, s.toFloat() / segments, 1f, 6f)
            d.vert(cx * topRadius, 1f, cz * topRadius, nx, ny, nz, s.toFloat() / segments, 0f, 6f)
        }
        for (s in 0 until segments) {
            val b = s * 2
            d.tri(b, b + 2, b + 1)
            d.tri(b + 1, b + 2, b + 3)
        }
        if (caps) {
            for ((y, r, ny) in listOf(Triple(0f, 1f, -1f), Triple(1f, topRadius, 1f))) {
                if (r <= 0.001f) continue
                val center = d.n
                d.vert(0f, y, 0f, 0f, ny, 0f, 0.5f, 0.5f, 6f)
                for (s in 0..segments) {
                    val a = (2 * PI * s / segments).toFloat()
                    d.vert(cos(a) * r, y, sin(a) * r, 0f, ny, 0f, 0.5f + cos(a) * 0.5f, 0.5f + sin(a) * 0.5f, 6f)
                }
                for (s in 0 until segments) d.tri(center, center + 1 + s, center + 2 + s)
            }
        }
        return d.build()
    }

    /** Sfera di raggio 1 centrata nell'origine. */
    fun sphere(rings: Int = 16, segments: Int = 24): Mesh {
        val d = MeshData()
        for (r in 0..rings) {
            val phi = (PI * r / rings).toFloat()
            for (s in 0..segments) {
                val th = (2 * PI * s / segments).toFloat()
                val x = sin(phi) * cos(th)
                val y = cos(phi)
                val z = sin(phi) * sin(th)
                d.vert(x, y, z, x, y, z, s.toFloat() / segments, r.toFloat() / rings, 6f)
            }
        }
        val row = segments + 1
        for (r in 0 until rings) for (s in 0 until segments) {
            val a = r * row + s
            d.tri(a, a + row, a + 1)
            d.tri(a + 1, a + row, a + row + 1)
        }
        return d.build()
    }
}
