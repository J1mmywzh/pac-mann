package model;

import model.MazeGraph.Direction;
import model.MazeGraph.MazeEdge;
import model.MazeGraph.MazeVertex;

public class PacMannManual extends PacMann{

    private final GameModel model;

    /**
     * Constructs a new manually-controlled PacMann.
     * @param model The game model this PacMann belongs to
     */
    public PacMannManual(GameModel model) {
        super(model);
        this.model = model;
    }

    @Override
    public MazeEdge nextEdge() {
        MazeVertex current = nearestVertex();

        // First try player's command direction
        Direction command = model.playerCommand();
        if (command != null) {
            MazeEdge edge = current.edgeInDirection(command);
            if (edge != null) {
                return edge;
            }
        }

        // Then try current movement direction
        if (location().edge() != null) {
            Direction currentDir = location().edge().direction();
            MazeEdge edge = current.edgeInDirection(currentDir);
            if (edge != null) {
                return edge;
            }
        }

        // No valid edge in either direction
        return null;
    }
}
