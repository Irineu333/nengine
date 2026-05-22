class CenterLine : Node() {

    @Inspect
    var x: Float = 400f

    @Inspect
    var height: Float = 600f

    override fun onRender(renderer: Renderer) {
        val dashHeight = 12f
        val gap = 8f
        val color = Color(1f, 1f, 1f, 0.3f)
        var y = 0f
        while (y < height) {
            renderer.drawRect(
                Rect(Vec2(x - 1f, y), Vec2(2f, dashHeight)),
                color,
                filled = true,
            )
            y += dashHeight + gap
        }
    }
}
