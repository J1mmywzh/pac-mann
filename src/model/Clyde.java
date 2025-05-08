package model;

import java.awt.Color;
import java.util.Random;
import model.MazeGraph.MazeVertex;

public class Clyde extends Ghost{

    /**
     * Construct a ghost associated to the given `model` with specified color and initial delay
     *
     * @param model
     */

    private final Random rand;

    public Clyde(GameModel model, Random rand) {
        super(model, Color.orange, 8000);
        this.rand = rand;
    }

    @Override
    protected MazeVertex target() {
        if(state == state.CHASE) {
            double pacX = model.pacMann().nearestVertex().loc().i();
            double pacY = model.pacMann().nearestVertex().loc().j();
            double clydeX = model.clyde().nearestVertex().loc().i();
            double clydeY = model.clyde().nearestVertex().loc().j();
            double xDiff = Math.pow(pacX - clydeX, 2);
            double yDiff = Math.pow(pacY - clydeY, 2);
            double dist = Math.pow(xDiff + yDiff, 0.5);
            if (dist > 10) {
                return model.pacMann().nearestVertex();
            }
            int xBound = rand.nextInt(model.width());
            int yBound = rand.nextInt(model.height());
            return model.graph.closestTo(xBound, yBound);

        }
        return model.graph.closestTo(model.width()-3, model.height()-3);
    }
}
