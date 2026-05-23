# extends BoxCollider

# Side carrier: ball.py reads `goal.name` for collision routing (leftGoal /
# rightGoal). The `side` export remains so future tooling / the Inspector can
# still surface the intent declaratively.
side: str = "Left"
