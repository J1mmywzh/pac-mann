package model;

import java.awt.Color;
import model.MazeGraph.MazeVertex;

public class Blinky extends Ghost{

    /**
     * Construct a ghost associated to the given `model` with specified color and initial delay
     *
     * @param model
     */
    public Blinky(GameModel model) {
        super(model, Color.red, 2000);
    }

    @Override
    protected MazeVertex target() {
        if (state() == GhostState.CHASE) {
            return model.pacMann().nearestVertex();
        } else {
            return model.graph().closestTo(2, 2);
        }
    }
}
