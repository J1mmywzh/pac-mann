package model;

import model.MazeGraph.Direction;
import model.MazeGraph.MazeEdge;
import model.MazeGraph.MazeVertex;

public class PacMannManual extends PacMann{

    private final GameModel model;

    /**
     * Constructs a new manually-controlled PacMann based on player input.
     * First tries to return the edge in the direction of the player's most recent command.
     * If movement in that direction isn't possible, attempts to continue in the current direction.
     * Returns null if neither option is possible.
     */
    public PacMannManual(GameModel model) {
        super(model);
        this.model = model;
    }

    @Override
    public MazeEdge nextEdge() {
        MazeVertex current = nearestVertex();

        // 1. Try player's command direction
        Direction command = model.playerCommand();
        if (command != null) {
            MazeEdge edge = current.edgeInDirection(command);
            if (edge != null) {
                return edge;
            }
        }

        // 2. Try current movement direction
        if (location().edge() != null) {
            Direction currentDir = location().edge().direction();
            MazeEdge edge = current.edgeInDirection(currentDir);
            if (edge != null) {
                return edge;
            }
        }

        // No valid edge in either direction -> stop moving
        return null;
    }
}
