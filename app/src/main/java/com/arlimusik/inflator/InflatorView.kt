package com.arlimusik.inflator

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class InflatorView(context: Context) : View(context) {

    private data class Param(val name: String, var value: Float)

    private val params = mutableListOf(
        Param("SATURATION", .56f),
        Param("HARMONICS", .48f),
        Param("PRESENCE", .40f),
        Param("TRANSIENT", .52f),
        Param("SOFT CLIP", .62f),
        Param("MIX", .78f),
        Param("INPUT", .50f),
        Param("OUTPUT", .50f),
        Param("CEILING", .88f)
    )

    private val teal2 = Color.rgb(10, 112, 109)
    private val tealDark = Color.rgb(4, 32, 34)
    private val gold = Color.rgb(232, 189, 77)
    private val cream = Color.rgb(246, 227, 170)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = -1
    private var lastY = 0f
    private var bypass = false
    private var abB = false
    private var oversampling = 2
    private var playing = false
    private var audioThread: Thread? = null
    @Volatile private var running = false
    private var phase = 0.0
    private var meterIn = .35f
    private var meterOut = .56f
    private var meterGr = .18f

    init {
        isFocusable = true
        keepScreenOn = true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()

        val bg = LinearGradient(0f, 0f, 0f, h, teal2, tealDark, Shader.TileMode.CLAMP)
        paint.shader = bg
        c.drawRoundRect(8f, 8f, w-8f, h-8f, 28f, 28f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = gold
        c.drawRoundRect(12f, 12f, w-12f, h-12f, 26f, 26f, paint)
        paint.style = Paint.Style.FILL

        paint.color = gold
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = h * .060f
        c.drawText("ARLIMUSIK STUDIO", w*.045f, h*.100f, paint)
        paint.textSize = h * .030f
        paint.color = cream
        c.drawText("INFLATOR 2.1  •  ANDROID TABLET EDITION", w*.047f, h*.142f, paint)

        drawMeters(c, w, h)

        val cols = 5
        val left = w*.065f
        val top = h*.30f
        val areaW = w*.67f
        val areaH = h*.56f
        for (i in params.indices) {
            val r = i / cols
            val col = i % cols
            val cx = left + col * (areaW/(cols-1))
            val cy = top + r * areaH
            drawKnob(c, cx, cy, min(w,h)*.058f, params[i], i == active)
        }

        drawButton(c, w*.805f, h*.36f, w*.12f, h*.075f, "BYPASS", bypass)
        drawButton(c, w*.805f, h*.47f, w*.12f, h*.075f, if(abB) "A / B : B" else "A / B : A", abB)
        drawButton(c, w*.805f, h*.58f, w*.12f, h*.075f, "OS ${oversampling}x", true)
        drawButton(c, w*.805f, h*.71f, w*.12f, h*.095f, if(playing) "STOP TONE" else "PLAY TONE", playing)

        val ax0 = w*.765f
        val ay0 = h*.18f
        val aw = w*.19f
        val ah = h*.10f
        paint.color = Color.argb(110, 0, 0, 0)
        c.drawRoundRect(ax0, ay0, ax0+aw, ay0+ah, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = gold
        val path = Path()
        for (i in 0..60) {
            val x = ax0 + aw*i/60f
            val p = i/60f
            val amp = (sin(p*10.0 + phase)*0.16 + sin(p*27.0)*0.07 + 0.55)
            val y = (ay0 + ah - (amp.toFloat()*ah*.82f)).coerceIn(ay0+4, ay0+ah-4)
            if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        if(playing) {
            phase += .10
            meterIn = (.28 + .22 * abs(sin(phase))).toFloat()
            meterOut = (.44 + .38 * abs(sin(phase*.91))).toFloat()
            meterGr = (.08 + .30 * abs(sin(phase*.67))).toFloat()
            postInvalidateDelayed(35)
        }
    }

    private fun drawKnob(c: Canvas, cx: Float, cy: Float, r: Float, p: Param, selected: Boolean) {
        paint.shader = RadialGradient(cx-r*.28f, cy-r*.3f, r*1.1f,
            intArrayOf(Color.rgb(72,126,122), Color.rgb(17,68,68), Color.rgb(3,25,27)),
            floatArrayOf(0f,.57f,1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx,cy,r,paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if(selected) 5f else 3f
        paint.color = if(selected) cream else gold
        c.drawCircle(cx,cy,r,paint)

        val a = Math.toRadians((135.0 + 270.0 * p.value).coerceIn(135.0,405.0))
        val x1 = cx + cos(a).toFloat()*r*.25f
        val y1 = cy + sin(a).toFloat()*r*.25f
        val x2 = cx + cos(a).toFloat()*r*.78f
        val y2 = cy + sin(a).toFloat()*r*.78f
        paint.strokeWidth = 5f
        paint.color = cream
        c.drawLine(x1,y1,x2,y2,paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.CENTER
        paint.color = gold
        paint.textSize = r*.31f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText(p.name, cx, cy+r*1.48f, paint)
        paint.color = cream
        paint.textSize = r*.28f
        c.drawText("${(p.value*100).roundToInt()}", cx, cy+r*1.82f, paint)
    }

    private fun drawButton(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, text: String, on: Boolean) {
        paint.color = if(on) Color.rgb(15,112,106) else Color.rgb(7,51,53)
        c.drawRoundRect(x,y,x+bw,y+bh,14f,14f,paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = gold
        c.drawRoundRect(x,y,x+bw,y+bh,14f,14f,paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = cream
        paint.textSize = bh*.36f
        paint.typeface = Typeface.DEFAULT_BOLD
        c.drawText(text, x+bw/2, y+bh*.62f, paint)
    }

    private fun drawMeters(c: Canvas, w: Float, h: Float) {
        val x0=w*.44f
        val y=h*.075f
        val mw=w*.085f
        val mh=h*.020f
        fun meter(label:String, v:Float, idx:Int) {
            val yy=y+idx*h*.037f
            paint.textAlign=Paint.Align.RIGHT
            paint.textSize=h*.019f
            paint.color=gold
            c.drawText(label, x0-8, yy+mh*.85f, paint)
            paint.color=Color.rgb(2,28,29)
            c.drawRoundRect(x0,yy,x0+mw,yy+mh,6f,6f,paint)
            paint.color=cream
            c.drawRoundRect(x0,yy,x0+mw*v.coerceIn(0f,1f),yy+mh,6f,6f,paint)
        }
        meter("IN",meterIn,0)
        meter("OUT",meterOut,1)
        meter("GR",meterGr,2)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w=width.toFloat(); val h=height.toFloat()
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if(hit(e.x,e.y,w*.805f,h*.36f,w*.12f,h*.075f)){ bypass=!bypass; invalidate(); return true }
                if(hit(e.x,e.y,w*.805f,h*.47f,w*.12f,h*.075f)){ abB=!abB; invalidate(); return true }
                if(hit(e.x,e.y,w*.805f,h*.58f,w*.12f,h*.075f)){
                    oversampling = when(oversampling){1->2;2->4;4->8;else->1}
                    invalidate(); return true
                }
                if(hit(e.x,e.y,w*.805f,h*.71f,w*.12f,h*.095f)){
                    if(playing) stopAudio() else startAudio()
                    invalidate(); return true
                }

                val cols=5
                val left=w*.065f
                val top=h*.30f
                val areaW=w*.67f
                val areaH=h*.56f
                val rad=min(w,h)*.075f
                active=-1
                for(i in params.indices){
                    val r=i/cols; val col=i%cols
                    val cx=left+col*(areaW/(cols-1)); val cy=top+r*areaH
                    if(hypot(e.x-cx,e.y-cy)<rad){ active=i; lastY=e.y; break }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if(active>=0){
                    val dy=lastY-e.y
                    params[active].value=(params[active].value+dy/(height*.55f)).coerceIn(0f,1f)
                    lastY=e.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { active=-1; invalidate() }
        }
        return true
    }

    private fun hit(px:Float,py:Float,x:Float,y:Float,w:Float,h:Float)=px in x..(x+w) && py in y..(y+h)

    private fun startAudio() {
        if(running) return
        running=true; playing=true
        audioThread=Thread {
            val sr=48000
            val minBuf=AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val track=AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(max(minBuf, sr/4*8))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            val frames=512
            val buf=FloatArray(frames*2)
            var ph=0.0
            val f=220.0
            track.play()
            while(running){
                val sat=params[0].value
                val harm=params[1].value
                val pres=params[2].value
                val soft=params[4].value
                val mix=params[5].value
                val input=0.25f + params[6].value*1.8f
                val output=0.25f + params[7].value*1.6f
                val ceiling=0.35f + params[8].value*0.64f
                for(i in 0 until frames){
                    val dry=(sin(ph)+0.22*sin(ph*2.0)+0.08*sin(ph*3.0)).toFloat()*0.26f*input
                    var wet=dry
                    if(!bypass){
                        val drive=1f+sat*9f
                        wet=tanh(wet*drive)
                        wet += (harm*.16f*tanh(wet*3.5f))
                        wet *= (1f+pres*.18f)
                        val clipAmount=1f+soft*2.5f
                        wet=(tanh(wet*clipAmount)/tanh(clipAmount))*ceiling
                        wet=dry*(1f-mix)+wet*mix
                    }
                    val out=(wet*output).coerceIn(-ceiling,ceiling)
                    buf[i*2]=out; buf[i*2+1]=out
                    ph += 2.0*Math.PI*f/sr
                    if(ph>2*Math.PI) ph-=2*Math.PI
                }
                track.write(buf,0,buf.size,AudioTrack.WRITE_BLOCKING)
            }
            track.stop(); track.release()
        }.apply { start() }
        postInvalidate()
    }

    fun stopAudio() {
        running=false; playing=false
        try { audioThread?.join(250) } catch(_:Throwable){}
        audioThread=null
        postInvalidate()
    }
}
