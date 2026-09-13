package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MeshFactory {
    fun plane(halfHeight: Float): Mesh = segmentedPlane(1, 1, halfHeight)
    fun segmentedPlane(widthSegments: Int, heightSegments: Int, halfHeight: Float = 1f): Mesh {
        require(widthSegments > 0 && heightSegments > 0)
        val vertexCount = (widthSegments + 1) * (heightSegments + 1); val vertices = FloatArray(vertexCount * 5); var p = 0
        for (y in 0..heightSegments) for (x in 0..widthSegments) {
            vertices[p++] = -1f + 2f*x/widthSegments; vertices[p++] = -halfHeight + 2f*halfHeight*y/heightSegments; vertices[p++] = 0f
            vertices[p++] = x.toFloat()/widthSegments; vertices[p++] = 1f-y.toFloat()/heightSegments
        }
        val indices=ShortArray(widthSegments*heightSegments*6); p=0
        for(y in 0 until heightSegments) for(x in 0 until widthSegments){ val bl=y*(widthSegments+1)+x; val br=bl+1; val tl=bl+widthSegments+1; val tr=tl+1; indices[p++]=bl.toShort();indices[p++]=br.toShort();indices[p++]=tl.toShort();indices[p++]=br.toShort();indices[p++]=tr.toShort();indices[p++]=tl.toShort() }
        return Mesh(ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(vertices);position(0)}, ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply{put(indices);position(0)},vertexCount,indices.size)
    }
    fun cube(size: Float = 1f): Mesh {
        require(size > 0f); val h=size/2f
        val faces=arrayOf(floatArrayOf(-h,-h,h, h,-h,h, h,h,h, -h,h,h),floatArrayOf(h,-h,-h, -h,-h,-h, -h,h,-h, h,h,-h),floatArrayOf(-h,-h,-h, -h,-h,h, -h,h,h, -h,h,-h),floatArrayOf(h,-h,h, h,-h,-h, h,h,-h, h,h,h),floatArrayOf(-h,h,h, h,h,h, h,h,-h, -h,h,-h),floatArrayOf(-h,-h,-h, h,-h,-h, h,-h,h, -h,-h,h))
        val data=FloatArray(24*5); var p=0; for(face in faces){for(i in 0 until 4){data[p++]=face[i*3];data[p++]=face[i*3+1];data[p++]=face[i*3+2];data[p++]=if(i==1||i==2)1f else 0f;data[p++]=if(i>=2)0f else 1f}}
        val idx=ShortArray(36);p=0;for(f in 0 until 6){val b=f*4;idx[p++]=b.toShort();idx[p++]=(b+1).toShort();idx[p++]=(b+2).toShort();idx[p++]=b.toShort();idx[p++]=(b+2).toShort();idx[p++]=(b+3).toShort()};return mesh(data,idx)
    }
    fun uvSphere(segments: Int = 24, rings: Int = 16, radius: Float = 1f): Mesh {
        require(segments >= 3 && rings >= 2 && radius > 0f); val vertices=FloatArray((segments+1)*(rings+1)*5);var p=0
        for(r in 0..rings){val v=r.toFloat()/rings;val phi=Math.PI*v;val y=(kotlin.math.cos(phi)*radius).toFloat();val rr=(kotlin.math.sin(phi)*radius).toFloat();for(s in 0..segments){val u=s.toFloat()/segments;val theta=2.0*Math.PI*u;vertices[p++]=(kotlin.math.cos(theta)*rr).toFloat();vertices[p++]=y;vertices[p++]=(kotlin.math.sin(theta)*rr).toFloat();vertices[p++]=u;vertices[p++]=1f-v}}
        val indices=ShortArray(segments*rings*6);p=0;for(r in 0 until rings)for(s in 0 until segments){val a=r*(segments+1)+s;val b=a+segments+1;indices[p++]=a.toShort();indices[p++]=b.toShort();indices[p++]=(a+1).toShort();indices[p++]=(a+1).toShort();indices[p++]=b.toShort();indices[p++]=(b+1).toShort()};return mesh(vertices,indices)
    }
    fun cylinder(radius: Float = 1f, height: Float = 1f, segments: Int = 16): Mesh {
        require(radius>0f&&height>0f&&segments>=3); val count=(segments+1)*2+2;val vertices=FloatArray(count*5);var p=0;val half=height/2f
        for(y in 0..1){val py=if(y==0)-half else half;for(s in 0..segments){val u=s.toFloat()/segments;val a=2.0*Math.PI*u;vertices[p++]=(kotlin.math.cos(a)*radius).toFloat();vertices[p++]=py;vertices[p++]=(kotlin.math.sin(a)*radius).toFloat();vertices[p++]=u;vertices[p++]=y.toFloat()}}
        val bottom=(segments+1)*2;vertices[p++]=0f;vertices[p++]=-half;vertices[p++]=0f;vertices[p++]=.5f;vertices[p++]=.5f;val top=bottom+1;vertices[p++]=0f;vertices[p++]=half;vertices[p++]=0f;vertices[p++]=.5f;vertices[p++]=.5f
        val indices=ShortArray(segments*12);p=0;for(s in 0 until segments){val a=s;val b=s+1;val c=segments+1+s;val d=c+1;indices[p++]=a.toShort();indices[p++]=c.toShort();indices[p++]=b.toShort();indices[p++]=b.toShort();indices[p++]=c.toShort();indices[p++]=d.toShort();indices[p++]=bottom.toShort();indices[p++]=b.toShort();indices[p++]=a.toShort();indices[p++]=top.toShort();indices[p++]=c.toShort();indices[p++]=d.toShort()};return mesh(vertices,indices)
    }
    fun hexTile(radius: Float = 1f): Mesh {
        require(radius>0f);val vertices=FloatArray(7*5);var p=0;vertices[p++]=0f;vertices[p++]=0f;vertices[p++]=0f;vertices[p++]=.5f;vertices[p++]=.5f;for(i in 0..5){val a=Math.PI/3.0*i;vertices[p++]=(kotlin.math.cos(a)*radius).toFloat();vertices[p++]=(kotlin.math.sin(a)*radius).toFloat();vertices[p++]=0f;vertices[p++]=(.5+kotlin.math.cos(a)*.5).toFloat();vertices[p++]=(.5-kotlin.math.sin(a)*.5).toFloat()};val indices=ShortArray(18);p=0;for(i in 0..5){indices[p++]=0;indices[p++]=(i+1).toShort();indices[p++]=if(i==5)1 else(i+2).toShort()};return mesh(vertices,indices)
    }
    private fun mesh(vertices: FloatArray, indices: ShortArray): Mesh = Mesh(ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(vertices);position(0)},ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply{put(indices);position(0)},vertices.size/5,indices.size)
}
