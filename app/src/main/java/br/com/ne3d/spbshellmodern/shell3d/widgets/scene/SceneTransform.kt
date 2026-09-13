package br.com.ne3d.spbshellmodern.shell3d.widgets.scene

/** Mutable GL-owned transform. Its matrix is reused by the graph traversal. */
class SceneTransform {
    var x = 0f; var y = 0f; var z = 0f
    var rotationX = 0f; var rotationY = 0f; var rotationZ = 0f
    var scaleX = 1f; var scaleY = 1f; var scaleZ = 1f
    var alpha = 1f
    fun reset() { x=0f; y=0f; z=0f; rotationX=0f; rotationY=0f; rotationZ=0f; scaleX=1f; scaleY=1f; scaleZ=1f; alpha=1f }
}

/** Allocation-free 4x4 operations, column-major to match GLES. */
internal object SceneMatrix {
    fun identity(out: FloatArray) { var i=0; while(i<16){ out[i]=if(i%5==0)1f else 0f; i++ } }
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        var c=0; while(c<4){ val b0=b[c*4]; val b1=b[c*4+1]; val b2=b[c*4+2]; val b3=b[c*4+3]
            out[c*4]=a[0]*b0+a[4]*b1+a[8]*b2+a[12]*b3; out[c*4+1]=a[1]*b0+a[5]*b1+a[9]*b2+a[13]*b3
            out[c*4+2]=a[2]*b0+a[6]*b1+a[10]*b2+a[14]*b3; out[c*4+3]=a[3]*b0+a[7]*b1+a[11]*b2+a[15]*b3; c++ }
    }
    fun local(out: FloatArray, t: SceneTransform) {
        identity(out); out[12]=t.x; out[13]=t.y; out[14]=t.z
        rotateX(out,t.rotationX); rotateY(out,t.rotationY); rotateZ(out,t.rotationZ)
        out[0]*=t.scaleX; out[1]*=t.scaleX; out[2]*=t.scaleX
        out[4]*=t.scaleY; out[5]*=t.scaleY; out[6]*=t.scaleY
        out[8]*=t.scaleZ; out[9]*=t.scaleZ; out[10]*=t.scaleZ
    }
    private fun rotateX(m: FloatArray, d: Float) { if(d==0f)return; val r=Math.toRadians(d.toDouble()); val c=kotlin.math.cos(r).toFloat();val s=kotlin.math.sin(r).toFloat(); val a4=m[4];val a5=m[5];val a6=m[6];val a7=m[7];val a8=m[8];val a9=m[9];val a10=m[10];val a11=m[11];m[4]=a4*c+a8*s;m[5]=a5*c+a9*s;m[6]=a6*c+a10*s;m[7]=a7*c+a11*s;m[8]=a8*c-a4*s;m[9]=a9*c-a5*s;m[10]=a10*c-a6*s;m[11]=a11*c-a7*s }
    private fun rotateY(m: FloatArray, d: Float) { if(d==0f)return; val r=Math.toRadians(d.toDouble()); val c=kotlin.math.cos(r).toFloat();val s=kotlin.math.sin(r).toFloat(); val a0=m[0];val a1=m[1];val a2=m[2];val a3=m[3];val a8=m[8];val a9=m[9];val a10=m[10];val a11=m[11];m[0]=a0*c-a8*s;m[1]=a1*c-a9*s;m[2]=a2*c-a10*s;m[3]=a3*c-a11*s;m[8]=a0*s+a8*c;m[9]=a1*s+a9*c;m[10]=a2*s+a10*c;m[11]=a3*s+a11*c }
    private fun rotateZ(m: FloatArray, d: Float) { if(d==0f)return; val r=Math.toRadians(d.toDouble()); val c=kotlin.math.cos(r).toFloat();val s=kotlin.math.sin(r).toFloat(); val a0=m[0];val a1=m[1];val a2=m[2];val a3=m[3];val a4=m[4];val a5=m[5];val a6=m[6];val a7=m[7];m[0]=a0*c+a4*s;m[1]=a1*c+a5*s;m[2]=a2*c+a6*s;m[3]=a3*c+a7*s;m[4]=a4*c-a0*s;m[5]=a5*c-a1*s;m[6]=a6*c-a2*s;m[7]=a7*c-a3*s }
}
