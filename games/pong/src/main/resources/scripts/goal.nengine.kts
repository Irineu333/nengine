enum class GoalSide {
    Left,
    Right
}

class Goal : BoxCollider() {
    @Inspect
    var side: GoalSide = GoalSide.Left
}
