package model;

import java.awt.Color;
import model.MazeGraph.Direction;
import model.MazeGraph.IPair;
import model.MazeGraph.MazeVertex;

public class Inky extends Ghost{

    /**
     * Construct a ghost associated to the given `model` with specified color and initial delay
     *
     * @param model
     */
    public Inky(GameModel model) {
        super(model, Color.cyan, 6000);
    }

    @Override
    protected MazeVertex target() {
        if (state == state.CHASE) {
            IPair pacAhead = new IPair(model.pacMann().nearestVertex().loc().i(),
                    model.pacMann().nearestVertex().loc().j());
            MazeVertex blinkyPos = model.blinky().nearestVertex();
            int i = 2 * pacAhead.i() - blinkyPos.loc().i();
            int j = 2 * pacAhead.j() - blinkyPos.loc().j();

            return model.graph().closestTo(i, j);
        }
        return model.graph().closestTo(2, model.height()-3);
    }
}

