package model;

import static org.junit.jupiter.api.Assertions.*;

import graph.Vertex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import model.MazeGraph.Direction;
import model.MazeGraph.MazeEdge;
import model.MazeGraph.IPair;
import model.MazeGraph.MazeVertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.GameMap;
import util.MazeGenerator.TileType;

public class MazeGraphTest {

    /* Note, to conform to the precondition of the `MazeGraph` constructor, make sure that any
     * TileType arrays that you construct contain a `PATH` tile at index [2][2] and represent a
     * single, orthogonally connected component of `PATH` tiles. */

    /**
     * Create a game map with tile types corresponding to the letters on each line of `template`.
     * 'w' = WALL, 'p' = PATH, and 'g' = GHOSTBOX.  The letters of `template` must form a rectangle.
     * Elevations will be a gradient from the top-left to the bottom-right corner with a horizontal
     * slope of 2 and a vertical slope of 1.
     */
    static GameMap createMap(String template) {
        Scanner lines = new Scanner(template);
        ArrayList<ArrayList<TileType>> lineLists = new ArrayList<>();

        while (lines.hasNextLine()) {
            ArrayList<TileType> lineList = new ArrayList<>();
            for (char c : lines.nextLine().toCharArray()) {
                switch (c) {
                    case 'w' -> lineList.add(TileType.WALL);
                    case 'p' -> lineList.add(TileType.PATH);
                    case 'g' -> lineList.add(TileType.GHOSTBOX);
                }
            }
            lineLists.add(lineList);
        }

        int height = lineLists.size();
        int width = lineLists.getFirst().size();

        TileType[][] types = new TileType[width][height];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                types[i][j] = lineLists.get(j).get(i);
            }
        }

        double[][] elevations = new double[width][height];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                elevations[i][j] = (2.0 * i + j);
            }
        }
        return new GameMap(types, elevations);
    }

    @DisplayName("WHEN a GameMap with exactly one path tile in position [2][2] is passed into the "
            + "MazeGraph constructor, THEN a graph with one vertex is created.")
    @Test
    void testOnePathCell() {
        GameMap map = createMap("""
                wwwww
                wwwww
                wwpww
                wwwww
                wwwww""");
        MazeGraph graph = new MazeGraph(map);
        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        assertEquals(1, vertices.size());
        assertTrue(vertices.containsKey(new IPair(2, 2)));
    }

    @DisplayName("WHEN a GameMap with exactly two horizontally adjacent path tiles is passed into "
            + "the MazeGraph constructor, THEN a graph with two vertices is created in which the two "
            + "vertices are connected by two directed edges with weights determined by evaluating "
            + "`MazeGraph.edgeWeight` on their elevations.")
    @Test
    void testTwoPathCellsHorizontal() {
        GameMap map = createMap("""
                wwwww
                wwwww
                wwppw
                wwwww
                wwwww""");
        MazeGraph graph = new MazeGraph(map);
        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        // graph contains two vertices with the correct locations
        assertEquals(2, vertices.size());
        IPair left = new IPair(2, 2);
        IPair right = new IPair(3, 2);
        assertTrue(vertices.containsKey(left));
        assertTrue(vertices.containsKey(right));

        MazeVertex vl = vertices.get(left);
        MazeVertex vr = vertices.get(right);

        // left vertex has one edge to the vertex to its right
        assertNull(vl.edgeInDirection(Direction.LEFT));
        assertNull(vl.edgeInDirection(Direction.UP));
        assertNull(vl.edgeInDirection(Direction.DOWN));
        MazeEdge l2r = vl.edgeInDirection(Direction.RIGHT);
        assertNotNull(l2r);

        // edge from left to right has the correct fields
        double lElev = map.elevations()[2][2];
        double rElev = map.elevations()[3][2];
        assertEquals(vl, l2r.src());
        assertEquals(vr, l2r.dst());
        assertEquals(Direction.RIGHT, l2r.direction());
        assertEquals(MazeGraph.edgeWeight(lElev, rElev), l2r.weight());

        // right vertex has one edge to the vertex to its left with the correct fields
        assertNull(vr.edgeInDirection(Direction.RIGHT));
        assertNull(vr.edgeInDirection(Direction.UP));
        assertNull(vr.edgeInDirection(Direction.DOWN));
        MazeEdge r2l = vr.edgeInDirection(Direction.LEFT);
        assertNotNull(r2l);
        assertEquals(vr, r2l.src());
        assertEquals(vl, r2l.dst());
        assertEquals(Direction.LEFT, r2l.direction());
        assertEquals(MazeGraph.edgeWeight(rElev, lElev), r2l.weight());
    }

    // Helper method to assert an edge connects two vertices
    private void assertEdgeConnects(MazeVertex src, Direction dir, MazeVertex expectedDst) {
        MazeEdge edge = src.edgeInDirection(dir);
        assertNotNull(edge);
        assertEquals(expectedDst.loc(), edge.dst().loc());
    }

    // Helper method to verify edge weight calculations
    private void assertEdgeWeightCorrect(MazeVertex src, Direction dir, MazeVertex dst,
            GameMap map) {
        MazeEdge edge = src.edgeInDirection(dir);
        double srcElev = map.elevations()[src.loc().i()][src.loc().j()];
        double dstElev = map.elevations()[dst.loc().i()][dst.loc().j()];
        assertEquals(MazeGraph.edgeWeight(srcElev, dstElev), edge.weight());
    }

    // Helper method to assert vertex has no outgoing edges
    private void assertNoEdges(MazeVertex vertex) {
        assertNull(vertex.edgeInDirection(Direction.UP));
        assertNull(vertex.edgeInDirection(Direction.DOWN));
        assertNull(vertex.edgeInDirection(Direction.LEFT));
        assertNull(vertex.edgeInDirection(Direction.RIGHT));
    }

    @DisplayName("WHEN a GameMap with exactly two vertically adjacent path tiles is passed into "
            + "the MazeGraph constructor, THEN a graph with two vertices is created in which the two "
            + "vertices are connected by two directed edges with weights determined by evaluating "
            + "`MazeGraph.edgeWeight` on their elevations.")
    @Test
    void testTwoPathCellsVertical() {
        GameMap map = createMap("""
                www
                wpw
                wpw
                www""");
        MazeGraph graph = new MazeGraph(map);
        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        // graph contains two vertices with the correct locations
        assertEquals(2, vertices.size());
        IPair top = new IPair(1, 1);
        IPair bottom = new IPair(1, 2);
        assertTrue(vertices.containsKey(top));
        assertTrue(vertices.containsKey(bottom));

        MazeVertex vt = vertices.get(top);
        MazeVertex vb = vertices.get(bottom);

        // use helper methods to check
        assertEdgeConnects(vt, Direction.DOWN, vb);
        assertEdgeWeightCorrect(vt, Direction.DOWN, vb, map);
        assertEdgeConnects(vb, Direction.UP, vt);
        assertEdgeWeightCorrect(vb, Direction.UP, vt, map);
    }

    @DisplayName("WHEN a GameMap includes two path tiles in the first and last column of the same "
            + "row, THEN (tunnel) edges are created between these tiles with the correct properties.")
    @Test
    void testHorizontalTunnelEdgeCreation() {
        GameMap map = createMap("""
                pwwp
                wwww
                wwww""");
        MazeGraph graph = new MazeGraph(map);
        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        // graph contains two vertices with the correct locations
        assertEquals(2, vertices.size());
        IPair left = new IPair(0, 0);
        IPair right = new IPair(3, 0);
        assertTrue(vertices.containsKey(left));
        assertTrue(vertices.containsKey(right));

        MazeVertex vl = vertices.get(left);
        MazeVertex vr = vertices.get(right);

        // use helper methods to check
        assertEdgeConnects(vl, Direction.LEFT, vr);
        assertEdgeWeightCorrect(vl, Direction.LEFT, vr, map);
        assertEdgeConnects(vr, Direction.RIGHT, vl);
        assertEdgeWeightCorrect(vr, Direction.RIGHT, vl, map);
    }

    @DisplayName("WHEN a GameMap includes a cyclic connected component of path tiles with a "
            + "non-path tiles in the middle, THEN its graph includes edges between all adjacent "
            + "pairs of vertices.")
    @Test
    void testCyclicPaths() {
        GameMap map = createMap("""
                wwwwwww
                wwwwwww
                wwpppww
                wwpwpww
                wwpppww
                wwwwwww""");
        MazeGraph graph = new MazeGraph(map);

        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        // Verify there are 8 path tiles in a square shape
        assertEquals(8, vertices.size());

        // Check all expected locations exist
        IPair[] expectedLocations = {
                new IPair(2, 2), new IPair(3, 2), new IPair(4, 2),
                new IPair(2, 3), new IPair(4, 3),
                new IPair(2, 4), new IPair(3, 4), new IPair(4, 4)
        };

        for (IPair loc : expectedLocations) {
            assertTrue(vertices.containsKey(loc));
        }

        // Get references to all vertices
        MazeVertex topLeft = vertices.get(new IPair(2, 2));
        MazeVertex topMid = vertices.get(new IPair(3, 2));
        MazeVertex topRight = vertices.get(new IPair(4, 2));
        MazeVertex leftMid = vertices.get(new IPair(2, 3));
        MazeVertex rightMid = vertices.get(new IPair(4, 3));
        MazeVertex bottomLeft = vertices.get(new IPair(2, 4));
        MazeVertex bottomMid = vertices.get(new IPair(3, 4));
        MazeVertex bottomRight = vertices.get(new IPair(4, 4));

        // Test horizontal connections in top row
        assertEdgeConnects(topLeft, Direction.RIGHT, topMid);
        assertEdgeConnects(topMid, Direction.LEFT, topLeft);
        assertEdgeConnects(topMid, Direction.RIGHT, topRight);
        assertEdgeConnects(topRight, Direction.LEFT, topMid);

        // Test horizontal connections in bottom row
        assertEdgeConnects(bottomLeft, Direction.RIGHT, bottomMid);
        assertEdgeConnects(bottomMid, Direction.LEFT, bottomLeft);
        assertEdgeConnects(bottomMid, Direction.RIGHT, bottomRight);
        assertEdgeConnects(bottomRight, Direction.LEFT, bottomMid);

        // Test vertical connections on left side
        assertEdgeConnects(topLeft, Direction.DOWN, leftMid);
        assertEdgeConnects(leftMid, Direction.UP, topLeft);
        assertEdgeConnects(leftMid, Direction.DOWN, bottomLeft);
        assertEdgeConnects(bottomLeft, Direction.UP, leftMid);

        // Test vertical connections on right side
        assertEdgeConnects(topRight, Direction.DOWN, rightMid);
        assertEdgeConnects(rightMid, Direction.UP, topRight);
        assertEdgeConnects(rightMid, Direction.DOWN, bottomRight);
        assertEdgeConnects(bottomRight, Direction.UP, rightMid);

        // Verify center tile (3,3) is not a vertex (it's a WALL)
        assertFalse(vertices.containsKey(new IPair(3, 3)));

        // Verify edge weights are calculated correctly
        assertEdgeWeightCorrect(topLeft, Direction.RIGHT, topMid, map);
        assertEdgeWeightCorrect(topMid, Direction.LEFT, topLeft, map);
        assertEdgeWeightCorrect(topMid, Direction.RIGHT, topRight, map);
        assertEdgeWeightCorrect(leftMid, Direction.DOWN, bottomLeft, map);
        // Add more edge weight checks as needed
    }

    @DisplayName("WHEN a GameMap has a cross-shaped path, THEN its graph connects all paths properly")
    @Test
    void testCrossShapedPaths() {
        GameMap map = createMap("""
                wwwww
                wwpww
                wpppw
                wwpww
                wwwww""");
        MazeGraph graph = new MazeGraph(map);
        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        // Verify there are 5 path tiles in a cross shape
        assertEquals(5, vertices.size());

        // Check all expected locations exist
        IPair center = new IPair(2, 2);
        IPair top = new IPair(2, 1);
        IPair bottom = new IPair(2, 3);
        IPair left = new IPair(1, 2);
        IPair right = new IPair(3, 2);

        assertTrue(vertices.containsKey(center));
        assertTrue(vertices.containsKey(top));
        assertTrue(vertices.containsKey(bottom));
        assertTrue(vertices.containsKey(left));
        assertTrue(vertices.containsKey(right));

        // Get references to all vertices
        MazeVertex centerV = vertices.get(center);
        MazeVertex topV = vertices.get(top);
        MazeVertex bottomV = vertices.get(bottom);
        MazeVertex leftV = vertices.get(left);
        MazeVertex rightV = vertices.get(right);

        // Test all connections from center
        assertEdgeConnects(centerV, Direction.UP, topV);
        assertEdgeConnects(centerV, Direction.DOWN, bottomV);
        assertEdgeConnects(centerV, Direction.LEFT, leftV);
        assertEdgeConnects(centerV, Direction.RIGHT, rightV);

        // Test all connections to center
        assertEdgeConnects(topV, Direction.DOWN, centerV);
        assertEdgeConnects(bottomV, Direction.UP, centerV);
        assertEdgeConnects(leftV, Direction.RIGHT, centerV);
        assertEdgeConnects(rightV, Direction.LEFT, centerV);

        // Verify edge weights
        assertEdgeWeightCorrect(centerV, Direction.UP, topV, map);
        assertEdgeWeightCorrect(centerV, Direction.DOWN, bottomV, map);
        assertEdgeWeightCorrect(centerV, Direction.LEFT, leftV, map);
        assertEdgeWeightCorrect(centerV, Direction.RIGHT, rightV, map);
    }

    @DisplayName("WHEN a GameMap has diagonal paths with dead ends, THEN its graph only connects orthogonally adjacent paths")
    @Test
    void testDiagonalPathsWithDeadEnds() {
        GameMap map = createMap("""
                pwwww
                wpwww
                wwpww
                wwwpw
                wwwwp""");
        MazeGraph graph = new MazeGraph(map);
        Map<IPair, MazeVertex> vertices = new HashMap<>();
        graph.vertices().forEach(v -> vertices.put(v.loc(), v));

        // Verify there are 5 path tiles in a diagonal line
        assertEquals(5, vertices.size());

        // Check all expected locations exist
        IPair[] pathLocations = {
                new IPair(0, 0),
                new IPair(1, 1),
                new IPair(2, 2),
                new IPair(3, 3),
                new IPair(4, 4)
        };

        for (IPair loc : pathLocations) {
            assertTrue(vertices.containsKey(loc));
        }

        // Get references to all vertices
        MazeVertex topLeft = vertices.get(pathLocations[0]);
        MazeVertex mid1 = vertices.get(pathLocations[1]);
        MazeVertex center = vertices.get(pathLocations[2]);
        MazeVertex mid2 = vertices.get(pathLocations[3]);
        MazeVertex bottomRight = vertices.get(pathLocations[4]);

        // Test that no connections exist in any direction
        assertNoEdges(topLeft);
        assertNoEdges(mid1);
        assertNoEdges(center);
        assertNoEdges(mid2);
        assertNoEdges(bottomRight);
    }
}
