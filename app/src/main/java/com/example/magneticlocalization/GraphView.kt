package com.example.magneticlocalization

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val nodes = mutableListOf<Triple<Pair<Int, Int>, Int, String>>() // 노드: (좌표, magnitude, 텍스트)
    private val edges = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>() // 간선

    private val obstacles = mutableListOf<Pair<Int, Int>>() // 노드: (좌표)

    private var cursor = Pair(0,0)
    
    private var gapLength = 27.54f
    private var correctionLength = 15f

    private val nodePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 10f
        textAlign = Paint.Align.CENTER
    }

    private val obstaclePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val edgePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val cursorPaint = Paint().apply {
        color = Color.DKGRAY             // 윤곽선 색상 설정
        strokeWidth = 5f               // 윤곽선 두께 설정
        style = Paint.Style.STROKE     // 내부 채우지 않고 윤곽선만 그리도록 설정
        isAntiAlias = true             // 부드러운 윤곽선
    }

    // 확대/축소 및 팬닝 관련 변수
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.2f, 5f) // 최소 0.2배, 최대 5배
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            offsetX -= distanceX / scaleFactor
            offsetY -= distanceY / scaleFactor
            invalidate()
            return true
        }
    })

    private var backgroundBitmap: Bitmap? = null

    init {
        // 배경 이미지 로드
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.floor_plan_1540) // 이미지 리소스 이름에 맞게 수정

        backgroundBitmap?.let { bitmap ->
            // 모눈 한칸의 길이 계산
//            gapLength = bitmap.width / 153.79f  // 154.0f 이어야 하는데 평면도가 조금 잘린듯 하다
            gapLength = bitmap.height / 66.0f
        }
    }

    fun getSettings(): Pair<Float, Float> {
        return Pair(gapLength, correctionLength)
    }
    fun editSettings(newGapLength: Float, newCorrectionLength: Float) {
        gapLength = newGapLength
        correctionLength = newCorrectionLength
    }

    fun addNode(position: Pair<Int, Int>, magnitude: Int) {
//        if (!nodes.any { it.first == position }) {
        if (nodes.isNotEmpty()) {
            val lastNode = nodes.last().first
            edges.add(Pair(lastNode, position)) // 간선 추가
        }
        nodes.add(Triple(position, magnitude, magnitude.toString()))
        obstacles.removeIf { it == position }
        invalidate()
//        }
    }

    fun addObstacleNode(position: Pair<Int, Int>) {
        obstacles.add(position)
        nodes.removeIf { it.first == position }
        invalidate()
    }

    fun removeNode(position: Pair<Int, Int>) {
        obstacles.removeIf { it == position }
        nodes.removeIf { it.first == position }
        edges.removeIf { (it.first == position) || (it.second == position) }
        invalidate()
    }

    fun clearNodes() {
        obstacles.clear()
        nodes.clear()
        edges.clear()
    }

    fun updateCursor(position: Pair<Int, Int>) {
        cursor = position

        offsetX = -position.first * gapLength
        offsetY = position.second * gapLength
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 캔버스에 스케일 및 이동 적용
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        canvas.translate(offsetX, offsetY)

        // 배경 이미지 그리기
        drawBackground(canvas)

        // 간선 그리기
//        edges.forEach { edge ->
//            drawEdge(canvas, edge.first, edge.second)
//        }

        // 노드 그리기
        nodes.forEach { node ->
            drawNode(canvas, node.first, node.third)
        }
        obstacles.forEach { pos ->
            drawObstacle(canvas, pos)
        }

        // 커서 표시
        drawCursor(canvas, cursor)

        canvas.restore()
    }


    private fun drawBackground(canvas: Canvas) {
        backgroundBitmap?.let { bitmap ->
            val (x, y) = mapToCanvas(Pair(0,0))
            canvas.drawBitmap(bitmap, x-correctionLength, y-correctionLength, null) // 노드가 모눈 안에 들어오도록 보정
        }
    }


    private fun drawNode(canvas: Canvas, position: Pair<Int, Int>, text: String) {
        val (x, y) = mapToCanvas(position)
        canvas.drawCircle(x, y, 10f, nodePaint) // 노드 원
        canvas.drawText(text, x, y + 3.3f, textPaint) // 노드 텍스트
    }

    private fun drawObstacle(canvas: Canvas, position: Pair<Int, Int>) {
        val (x, y) = mapToCanvas(position)
        canvas.drawRect(x+15f, y+15f, x-15f, y-15f, obstaclePaint)
    }

    private fun drawEdge(canvas: Canvas, from: Pair<Int, Int>, to: Pair<Int, Int>) {
        val (startX, startY) = mapToCanvas(from)
        val (endX, endY) = mapToCanvas(to)

        canvas.drawLine(startX, startY, endX, endY, edgePaint)
    }

    private fun drawCursor(canvas: Canvas, position: Pair<Int, Int>) {
        val (x, y) = mapToCanvas(position)
        canvas.drawCircle(x, y, 12f, cursorPaint)
    }

    private fun mapToCanvas(position: Pair<Int, Int>): Pair<Float, Float> {
//        val canvasX = width / 2f + position.first * gapLength
//        val canvasY = height / 2f - position.second * gapLength
        val canvasX = width / 2f + position.first * gapLength
        val canvasY = height / 2f - position.second * gapLength
        return Pair(canvasX, canvasY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event) // 확대/축소 동작 처리
        if (!scaleGestureDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled // 팬 동작 처리
        }
        return handled || super.onTouchEvent(event)
    }
}
