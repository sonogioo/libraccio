package it.libraccio.app.room3d

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Piccole utilità di algebra 4x4 (colonne, come OpenGL) e quaternioni. */
object M {
    fun identity() = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    fun mul(a: FloatArray, b: FloatArray) = FloatArray(16).also { Matrix.multiplyMM(it, 0, a, 0, b, 0) }
    fun chain(vararg ms: FloatArray): FloatArray = ms.reduce { acc, m -> mul(acc, m) }
    fun translate(x: Float, y: Float, z: Float) = identity().also { Matrix.translateM(it, 0, x, y, z) }
    fun scale(x: Float, y: Float, z: Float) = identity().also { Matrix.scaleM(it, 0, x, y, z) }
    fun rotate(deg: Float, x: Float, y: Float, z: Float) = identity().also { Matrix.rotateM(it, 0, deg, x, y, z) }

    fun invert(m: FloatArray) = FloatArray(16).also { if (!Matrix.invertM(it, 0, m, 0)) Matrix.setIdentityM(it, 0) }

    fun normalMatrix(model: FloatArray): FloatArray {
        val inv = invert(model)
        return FloatArray(16).also { Matrix.transposeM(it, 0, inv, 0) }
    }

    /** Trasforma un punto (w = 1) e divide per w. */
    fun point(m: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        val v = floatArrayOf(x, y, z, 1f)
        val r = FloatArray(4)
        Matrix.multiplyMV(r, 0, m, 0, v, 0)
        val w = if (r[3] == 0f) 1f else r[3]
        return floatArrayOf(r[0] / w, r[1] / w, r[2] / w, r[3])
    }

    /** Trasforma una direzione (w = 0). */
    fun dir(m: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        val r = FloatArray(4)
        Matrix.multiplyMV(r, 0, m, 0, floatArrayOf(x, y, z, 0f), 0)
        return floatArrayOf(r[0], r[1], r[2])
    }

    fun lookAt(eye: FloatArray, target: FloatArray) = FloatArray(16).also {
        Matrix.setLookAtM(it, 0, eye[0], eye[1], eye[2], target[0], target[1], target[2], 0f, 1f, 0f)
    }

    fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float) = FloatArray(16).also {
        Matrix.perspectiveM(it, 0, fovYDeg, aspect, near, far)
    }

    fun ortho(l: Float, r: Float, b: Float, t: Float, n: Float, f: Float) = FloatArray(16).also {
        Matrix.orthoM(it, 0, l, r, b, t, n, f)
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun lerp3(a: FloatArray, b: FloatArray, t: Float) = floatArrayOf(lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t))
    fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }
}

/** Quaternione (x, y, z, w). */
object Q {
    fun identity() = floatArrayOf(0f, 0f, 0f, 1f)

    fun axisAngle(ax: Float, ay: Float, az: Float, deg: Float): FloatArray {
        val r = Math.toRadians(deg.toDouble()) / 2
        val s = sin(r).toFloat()
        val len = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-6f)
        return floatArrayOf(ax / len * s, ay / len * s, az / len * s, cos(r).toFloat())
    }

    fun mul(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
    )

    fun slerp(a: FloatArray, b0: FloatArray, t: Float): FloatArray {
        var b = b0
        var d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        if (d < 0) { b = floatArrayOf(-b[0], -b[1], -b[2], -b[3]); d = -d }
        if (d > 0.9995f) {
            val r = FloatArray(4) { a[it] + (b[it] - a[it]) * t }
            val l = sqrt(r.sumOf { (it * it).toDouble() }).toFloat()
            return FloatArray(4) { r[it] / l }
        }
        val th = acos(d.toDouble())
        val s = sin(th)
        val wa = (sin((1 - t) * th) / s).toFloat()
        val wb = (sin(t * th) / s).toFloat()
        return FloatArray(4) { a[it] * wa + b[it] * wb }
    }

    fun toMatrix(q: FloatArray): FloatArray {
        val (x, y, z, w) = q
        return floatArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
            0f, 0f, 0f, 1f,
        )
    }
}

object Gl {
    private const val TAG = "Libraccio3D"

    fun program(vs: String, fs: String): Int {
        val v = shader(GLES30.GL_VERTEX_SHADER, vs)
        val f = shader(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            Log.e(TAG, "Link error: $log")
            throw IllegalStateException("Link error: $log")
        }
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val status = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            Log.e(TAG, "Shader error: $log")
            throw IllegalStateException("Shader error: $log")
        }
        return s
    }

    fun srgbToLinear(argb: Int): FloatArray {
        fun ch(v: Int): Float = Math.pow(v / 255.0, 2.2).toFloat()
        return floatArrayOf(ch((argb shr 16) and 0xFF), ch((argb shr 8) and 0xFF), ch(argb and 0xFF))
    }
}
