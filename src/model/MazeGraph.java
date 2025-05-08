package model;

import graph.Edge;
import graph.Vertex;
import java.util.LinkedList;
import java.util.Queue;
import util.MazeGenerator.TileType;
import java.util.HashMap;
import util.GameMap;

/**
 * A graph representing a game's maze, connecting the "path" tiles of a tile grid.
 */
public class MazeGraph {

    /* ****************************************************************
     * Helper types (defined here as nested types to avoid writing    *
     * even more .java files for each one)                            *
     **************************************************************** */

    /**
     * An ordered pair of integers.  In the context of the tile grid, `i` corresponds to the column
     * (horizontal coordinate, increasing to the right) and `j` corresponds to the row (vertical
     * direction, increasing down).
     */
    public record IPair(int i, int j) {

    }

    /**
     * The direction of a (directed) edge in this graph.
     */
    public enum Direction {
        LEFT, RIGHT, UP, DOWN;

        // Enums are still classes in Java, so they can have methods too.

        /**
         * Return the direction that is the opposite of this direction.
         */
        public Direction reverse() {
            return switch (this) {
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }
    }

    /**
     * A vertex in our graph, corresponding to a path tile in the tile grid.
     */
    public static class MazeVertex implements Vertex<MazeEdge> {

        /**
         * The location of this vertex's tile within the tile grid.
         */
        private final IPair loc;

        /**
         * This vertex's outgoing edges, each associated with the direction it points in.
         */
        private final HashMap<Direction, MazeEdge> edgeMap;


        /**
         * Construct a new vertex at location `loc` with no outgoing edges.
         */
        public MazeVertex(IPair loc) {
            this.loc = loc;
            edgeMap = new HashMap<>();
        }

        /**
         * Return the edge leaving this vertex in the direction `direction`, or null if no such edge
         * exists.  The direction of a "tunnel" edge is from the source to the grid's nearest
         * boundary (that is, an edge connecting a top tile to a bottom tile points "up").
         */
        public MazeEdge edgeInDirection(Direction direction) {
            return edgeMap.get(direction);
        }

        /**
         * Return the coordinates of this vertex's tile in the tile grid.
         */
        public IPair loc() {
            return loc;
        }

        @Override
        public Iterable<MazeEdge> outgoingEdges() {
            return edgeMap.values();
        }

        /**
         * Add `edge` as an outgoing edge from this vertex.  Requires that this vertex is the edge's
         * source and that no outgoing edge has already been added in the same direction. This
         * method has restricted visibility, as it is only meant to be called when constructing a
         * `MazeGraph`.
         */
        void addOutgoingEdge(MazeEdge edge) {
            assert edge.src().equals(this);
            assert !edgeMap.containsKey(edge.direction());
            edgeMap.put(edge.direction(), edge);
        }
    }

    /**
     * Represents a directed edge from `src` to `dst` with weight `weight`, which points in
     * direction `direction` on the tile grid.
     */
    public record MazeEdge(MazeVertex src, MazeVertex dst, Direction direction,
                           double weight) implements Edge<MazeVertex> {

        /**
         * Return the edge pointing from `dst` to `src` in the maze graph.  Requires that the maze
         * graph has been fully constructed.
         */
        public MazeEdge reverse() {
            return dst.edgeInDirection(direction.reverse());
        }
    }

    /* ****************************************************************
     * Fields of MazeGraph                                            *
     **************************************************************** */

    /**
     * The vertices of this graph, each associated with the location of its corresponding path tile
     * in the tile grid.
     */
    private final HashMap<IPair, MazeVertex> vertices;

    /**
     * The width of the tile grid defining this maze.
     */
    private final int width;

    /**
     * The height of the tile grid defining this maze.
     */
    private final int height;

    /**
     * Construct the maze graph corresponding to the tile grid `map`. Requires `map.types()[2][2]`
     * to be a `TileType.PATH` and that all `PATH` tiles belong to the same orthogonally connected
     * component. Requires `map.types()` and `map.elevations()` have the same shape, with the first
     * index corresponding to columns and the second index corresponding to rows.
     */
    public MazeGraph(GameMap map) {
        width = map.types().length;
        height = map.types()[0].length;
        vertices = new HashMap<>();

        /**
         * Completed using BFS to ensure each PATH tile is only processed once
         * */

        // Save original arrays
        TileType[][] types = map.types();
        double[][] elevations = map.elevations();

        // For every cell in the grid: if it's PATH and unseen, BFS its connected component and
        // add all PATH-tiles as vertices
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                IPair seed = new IPair(i, j);

                if (types[i][j] == TileType.PATH
                        && !vertices.containsKey(seed)) {

                    // New component → seed it
                    Queue<IPair> q = new LinkedList<>();
                    vertices.put(seed, new MazeVertex(seed));
                    q.add(seed);

                    // BFS traverses on all (new) vertices
                    while (!q.isEmpty()) {
                        // current tile
                        IPair loc = q.remove();
                        int x = loc.i();
                        int y = loc.j();
                        MazeVertex src = vertices.get(loc);

                        // Try all four directions
                        for (Direction dir : Direction.values()) {
                            int ni = x, nj = y;
                            switch (dir) {
                                case LEFT -> ni = (x - 1 + width) % width;
                                case RIGHT -> ni = (x + 1) % width;
                                case UP -> nj = (y - 1 + height) % height;
                                case DOWN -> nj = (y + 1) % height;
                            }

                            // Only continue on PATH tiles
                            if (types[ni][nj] != TileType.PATH) {
                                continue;
                            }

                            IPair neighborLoc = new IPair(ni, nj);

                            // If first time seeing tile -> create vertex and add to queue
                            if (!vertices.containsKey(neighborLoc)) {
                                vertices.put(neighborLoc, new MazeVertex(neighborLoc));
                                q.add(neighborLoc);
                            }

                            // Create directed edge src -> dst
                            MazeVertex dst = vertices.get(neighborLoc);
                            double weight = edgeWeight(elevations[x][y], elevations[ni][nj]);
                            MazeEdge edge = new MazeEdge(src, dst, dir, weight);
                            src.addOutgoingEdge(edge);
                        }
                    }
                }
            }
        }
    }

    /**
     * Return the weight that an edge should have if it connects a vertex with elevation `srcElev`
     * to a vertex with elevation `dstElev`.
     */
    static double edgeWeight(double srcElev, double dstElev) {
        // Uphill edges should have higher weight
        double elevDiff = Math.clamp(dstElev - srcElev, -0.25, 0.25);
        double weight = 1 + elevDiff * 3;
        assert weight >= 0;
        return weight;
    }

    /**
     * Return a vertex that is close to the tile location `(i, j)` (where `i` is column number and
     * `j` is row number).  Ghosts are expected to use this to ensure that they are targeting a
     * reachable path tile.  (Most of the time, this will be a closest such vertex if "tunnels" are
     * ignored.)
     */
    public MazeVertex closestTo(int i, int j) {
        // clamp i,j within maze bounds
        i = Math.clamp(i, 0, width - 2);
        j = Math.clamp(j, 0, height - 2);

        // The maze generator guarantees that tiles with coordinates (3x+2,3y+2) are path tiles.
        // (ip,jp) is the closest such coordinates to (i,j).
        int ip = (((i - 1) / 3) * 3 + 2);
        int jp = (((j - 1) / 3) * 3 + 2);

        for (IPair loc : new IPair[]{new IPair(i, j), new IPair(i, jp), new IPair(ip, j),
                new IPair(ip, jp)}) {
            if (vertices.containsKey(loc)) {
                return vertices.get(loc);
            }
        }

        // the only time we reach here is if (ip,jp) is inside the ghost box. In this case,
        // (ip,jp+3) is guaranteed to be a path tile outside the ghost box.
        assert (vertices.get(new IPair(ip, jp + 3)) != null);
        return vertices.get(new IPair(ip, jp + 3));
    }

    /**
     * Return the full collection of vertices in this graph.
     */
    public Iterable<MazeVertex> vertices() {
        return vertices.values();
    }

    /**
     * Return the first edge that PacMann will traverse at the start of a game.
     */
    public MazeEdge pacMannStartingEdge() {
        IPair startingLoc = new IPair((width - 1) / 2, 3 * ((3 * (height / 3) - 1) / 4) + 2);
        MazeVertex t = vertices.get(startingLoc);
        if (t.edgeMap.containsKey(Direction.LEFT)) {
            return t.edgeMap.get(Direction.LEFT).reverse();
        } else {
            return t.edgeMap.get(Direction.UP).reverse();
        }
    }

    /**
     * Return the first edge that a ghost will traverse upon transitioning from the WAIT to the
     * CHASE state.
     */
    public MazeEdge ghostStartingEdge() {
        IPair startingLoc = new IPair((width - 1) / 2, 3 * ((height - 3) / 6) - 1);
        MazeVertex s = vertices.get(startingLoc);
        return s.edgeMap.get(Direction.RIGHT);
    }
}
