package model;

import java.awt.Color;
import model.MazeGraph.Direction;
import model.MazeGraph.IPair;
import model.MazeGraph.MazeVertex;

public class Pinky extends Ghost {

    /**
     * Construct a ghost associated to the given `model` with specified color and initial delay
     *
     * @param model
     */
    public Pinky(GameModel model) {
        super(model, Color.pink, 4000);
    }

    @Override
    protected MazeVertex target() {
        if (state == GhostState.CHASE){
            return switch (model.pacMann().currentEdge().direction()) {
                case Direction.LEFT -> model.graph.closestTo(
                        model.pacMann().nearestVertex().loc().i() - 3,
                        model.pacMann().nearestVertex().loc().j());
                case Direction.RIGHT -> model.graph().closestTo(
                        model.pacMann().nearestVertex().loc().i() + 3,
                        model.pacMann().nearestVertex().loc().j());
                case Direction.UP -> model.graph().closestTo(
                        model.pacMann().nearestVertex().loc().i(),
                        model.pacMann().nearestVertex().loc().j() - 3);
                case Direction.DOWN -> model.graph().closestTo(
                        model.pacMann().nearestVertex().loc().i(),
                        model.pacMann().nearestVertex().loc().j() + 3);
            };
        }
        return model.graph.closestTo(model.width() - 3, 2);
    }
}